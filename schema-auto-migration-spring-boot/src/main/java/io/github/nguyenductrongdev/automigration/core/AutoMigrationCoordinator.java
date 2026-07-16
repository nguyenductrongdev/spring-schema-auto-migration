package io.github.nguyenductrongdev.automigration.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plans providers in parallel, validates every plan, then executes providers in parallel before
 * lifecycle components start.
 */
public class AutoMigrationCoordinator implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoMigrationCoordinator.class);

    private final List<SchemaAutoMigrationProvider> providers;

    public AutoMigrationCoordinator(List<SchemaAutoMigrationProvider> providers) {
        List<SchemaAutoMigrationProvider> ordered = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(ordered);
        this.providers = List.copyOf(ordered);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (providers.isEmpty()) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                providers.size(), new MigrationThreadFactory());
        try {
            migrate(executor);
        } finally {
            executor.shutdownNow();
        }
    }

    private void migrate(ExecutorService executor) {
        List<PreparationTask> preparationTasks = providers.stream()
                .map(provider -> new PreparationTask(
                        provider,
                        CompletableFuture.supplyAsync(() -> prepare(provider), executor)))
                .toList();

        List<PreparedProvider> preparedProviders = new ArrayList<>();
        RuntimeException failure = null;
        for (PreparationTask task : preparationTasks) {
            try {
                preparedProviders.add(new PreparedProvider(
                        task.provider(),
                        task.future().join()));
            } catch (CompletionException exception) {
                failure = appendFailure(
                        failure,
                        providerFailure("planning", task.provider(), exception.getCause()));
            }
        }

        for (PreparedProvider prepared : preparedProviders) {
            try {
                prepared.migration().validate();
            } catch (RuntimeException exception) {
                failure = appendFailure(
                        failure,
                        providerFailure("validation", prepared.provider(), exception));
            }
        }

        if (failure != null) {
            throw failure;
        }

        List<ExecutionTask> executionTasks = preparedProviders.stream()
                .map(prepared -> new ExecutionTask(
                        prepared.provider(),
                        CompletableFuture.runAsync(prepared.migration()::execute, executor)))
                .toList();
        for (ExecutionTask task : executionTasks) {
            try {
                task.future().join();
            } catch (CompletionException exception) {
                failure = appendFailure(
                        failure,
                        providerFailure("execution", task.provider(), exception.getCause()));
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private PreparedSchemaMigration prepare(SchemaAutoMigrationProvider provider) {
        LOGGER.debug("Planning {} schema auto-migration provider", provider.providerName());
        return provider.prepareMigration();
    }

    private RuntimeException providerFailure(
            String phase,
            SchemaAutoMigrationProvider provider,
            Throwable cause) {
        return new IllegalStateException(
                "Schema auto-migration " + phase + " failed for provider '" + provider.providerName() + "'",
                cause);
    }

    private RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private record PreparationTask(
            SchemaAutoMigrationProvider provider,
            CompletableFuture<PreparedSchemaMigration> future) {
    }

    private record PreparedProvider(
            SchemaAutoMigrationProvider provider,
            PreparedSchemaMigration migration) {
    }

    private record ExecutionTask(
            SchemaAutoMigrationProvider provider,
            CompletableFuture<Void> future) {
    }

    private static final class MigrationThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "schema-auto-migration-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}