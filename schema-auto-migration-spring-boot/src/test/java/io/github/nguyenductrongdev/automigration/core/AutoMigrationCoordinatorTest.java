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
        SchemaAutoMigrationProvider secondary = provider(
                "secondary",
                200,
                () -> {
                    await(planningBarrier);
                    return migration(
                            () -> validations.add("secondary"),
                            () -> {
                            });
                });
        SchemaAutoMigrationProvider primary = provider(
                "primary",
                100,
                () -> {
                    await(planningBarrier);
                    return migration(
                            () -> validations.add("primary"),
                            () -> {
                            });
                });

        new AutoMigrationCoordinator(List.of(secondary, primary))
                .afterSingletonsInstantiated();

        assertThat(validations).containsExactly("primary", "secondary");
    }

    @Test
    void validatesEveryPlanBeforeStartingAnyExecution() {
        List<String> validations = new CopyOnWriteArrayList<>();
        List<String> executions = new CopyOnWriteArrayList<>();
        SchemaAutoMigrationProvider primary = provider(
                "primary",
                100,
                () -> migration(
                        () -> validations.add("primary"),
                        () -> executions.add("primary")));
        SchemaAutoMigrationProvider secondary = provider(
                "secondary",
                200,
                () -> migration(
                        () -> {
                            validations.add("secondary");
                            throw new IllegalStateException("unsupported schema");
                        },
                        () -> executions.add("secondary")));

        AutoMigrationCoordinator coordinator =
                new AutoMigrationCoordinator(List.of(secondary, primary));

        assertThatThrownBy(coordinator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Schema auto-migration validation failed for provider 'secondary'")
                .hasRootCauseMessage("unsupported schema");
        assertThat(validations).containsExactly("primary", "secondary");
        assertThat(executions).isEmpty();
    }

    @Test
    void executesProvidersConcurrentlyAfterGlobalValidation() {
        CyclicBarrier executionBarrier = new CyclicBarrier(2);
        List<String> executions = new CopyOnWriteArrayList<>();
        SchemaAutoMigrationProvider primary = provider(
                "primary",
                100,
                () -> migration(
                        () -> {
                        },
                        () -> {
                            await(executionBarrier);
                            executions.add("primary");
                        }));
        SchemaAutoMigrationProvider secondary = provider(
                "secondary",
                200,
                () -> migration(
                        () -> {
                        },
                        () -> {
                            await(executionBarrier);
                            executions.add("secondary");
                        }));

        new AutoMigrationCoordinator(List.of(primary, secondary))
                .afterSingletonsInstantiated();

        assertThat(executions).containsExactlyInAnyOrder("primary", "secondary");
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
