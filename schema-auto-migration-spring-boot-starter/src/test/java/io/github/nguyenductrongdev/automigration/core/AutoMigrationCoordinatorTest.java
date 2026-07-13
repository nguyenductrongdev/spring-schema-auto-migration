package io.github.nguyenductrongdev.automigration.core;

import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoMigrationCoordinatorTest {

    @Test
    void preparesProvidersConcurrentlyAndValidatesInProviderOrder() {
        CyclicBarrier planningBarrier = new CyclicBarrier(2);
        List<String> validations = new CopyOnWriteArrayList<>();
        SchemaAutoMigrationProvider elasticsearch = provider(
                "elasticsearch",
                200,
                () -> {
                    await(planningBarrier);
                    return migration(
                            () -> validations.add("elasticsearch"),
                            () -> {
                            });
                });
        SchemaAutoMigrationProvider cassandra = provider(
                "cassandra",
                100,
                () -> {
                    await(planningBarrier);
                    return migration(
                            () -> validations.add("cassandra"),
                            () -> {
                            });
                });

        new AutoMigrationCoordinator(List.of(elasticsearch, cassandra))
                .afterSingletonsInstantiated();

        assertThat(validations).containsExactly("cassandra", "elasticsearch");
    }

    @Test
    void validatesEveryPlanBeforeStartingAnyExecution() {
        List<String> validations = new CopyOnWriteArrayList<>();
        List<String> executions = new CopyOnWriteArrayList<>();
        SchemaAutoMigrationProvider cassandra = provider(
                "cassandra",
                100,
                () -> migration(
                        () -> validations.add("cassandra"),
                        () -> executions.add("cassandra")));
        SchemaAutoMigrationProvider elasticsearch = provider(
                "elasticsearch",
                200,
                () -> migration(
                        () -> {
                            validations.add("elasticsearch");
                            throw new IllegalStateException("unsupported mapping");
                        },
                        () -> executions.add("elasticsearch")));

        AutoMigrationCoordinator coordinator =
                new AutoMigrationCoordinator(List.of(elasticsearch, cassandra));

        assertThatThrownBy(coordinator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schema auto-migration validation failed for provider 'elasticsearch'")
                .hasRootCauseMessage("unsupported mapping");
        assertThat(validations).containsExactly("cassandra", "elasticsearch");
        assertThat(executions).isEmpty();
    }

    @Test
    void executesProvidersConcurrentlyAfterGlobalValidation() {
        CyclicBarrier executionBarrier = new CyclicBarrier(2);
        List<String> executions = new CopyOnWriteArrayList<>();
        SchemaAutoMigrationProvider cassandra = provider(
                "cassandra",
                100,
                () -> migration(
                        () -> {
                        },
                        () -> {
                            await(executionBarrier);
                            executions.add("cassandra");
                        }));
        SchemaAutoMigrationProvider elasticsearch = provider(
                "elasticsearch",
                200,
                () -> migration(
                        () -> {
                        },
                        () -> {
                            await(executionBarrier);
                            executions.add("elasticsearch");
                        }));

        new AutoMigrationCoordinator(List.of(cassandra, elasticsearch))
                .afterSingletonsInstantiated();

        assertThat(executions).containsExactlyInAnyOrder("cassandra", "elasticsearch");
    }

    @Test
    void runsAfterSingletonInitializationAndBeforeLifecycleComponentsStart() {
        List<String> events = new ArrayList<>();
        SchemaAutoMigrationProvider provider = provider(
                "migration",
                100,
                () -> {
                    events.add("prepare");
                    return migration(
                            () -> events.add("validate"),
                            () -> events.add("execute"));
                });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("migrationProvider", SchemaAutoMigrationProvider.class, () -> provider);
            context.registerBean(
                    "autoMigrationCoordinator",
                    AutoMigrationCoordinator.class,
                    () -> new AutoMigrationCoordinator(List.of(provider)));
            context.registerBean(
                    "requestAcceptingLifecycle",
                    SmartLifecycle.class,
                    () -> new RequestAcceptingLifecycle(events));
            context.registerBean("starterSingleton", Object.class, () -> {
                events.add("starters-initialized");
                return new Object();
            });
            context.refresh();
        }

        assertThat(events).containsExactly(
                "starters-initialized",
                "prepare",
                "validate",
                "execute",
                "requests-accepted");
    }

    private SchemaAutoMigrationProvider provider(
            String name,
            int order,
            Supplier<PreparedSchemaMigration> preparation) {
        return new SchemaAutoMigrationProvider() {
            @Override
            public String providerName() {
                return name;
            }

            @Override
            public PreparedSchemaMigration prepareMigration() {
                return preparation.get();
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    private PreparedSchemaMigration migration(Runnable validation, Runnable execution) {
        return new PreparedSchemaMigration() {
            @Override
            public void validate() {
                validation.run();
            }

            @Override
            public void execute() {
                execution.run();
            }
        };
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Providers did not run concurrently", exception);
        }
    }

    private static final class RequestAcceptingLifecycle implements SmartLifecycle {

        private final List<String> events;
        private boolean running;

        private RequestAcceptingLifecycle(List<String> events) {
            this.events = events;
        }

        @Override
        public void start() {
            events.add("requests-accepted");
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
