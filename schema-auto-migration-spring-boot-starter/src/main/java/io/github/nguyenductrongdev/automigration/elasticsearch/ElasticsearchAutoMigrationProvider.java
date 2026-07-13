package io.github.nguyenductrongdev.automigration.elasticsearch;

import io.github.nguyenductrongdev.automigration.core.PreparedSchemaMigration;
import io.github.nguyenductrongdev.automigration.core.SchemaAutoMigrationProvider;
import io.github.nguyenductrongdev.automigration.elasticsearch.compare.ElasticsearchSchemaComparator;
import io.github.nguyenductrongdev.automigration.elasticsearch.execute.ElasticsearchMigrationExecutor;
import io.github.nguyenductrongdev.automigration.elasticsearch.inspect.ElasticsearchSchemaInspector;
import io.github.nguyenductrongdev.automigration.elasticsearch.log.ElasticsearchMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.elasticsearch.scan.ElasticsearchSchemaScanner;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;

/** Elasticsearch implementation of the shared schema migration provider contract. */
public class ElasticsearchAutoMigrationProvider implements SchemaAutoMigrationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAutoMigrationProvider.class);

    private final ElasticsearchAutoMigrationProperties properties;
    private final ElasticsearchOperations operations;
    private final ElasticsearchSchemaScanner scanner;
    private final ElasticsearchSchemaInspector inspector;
    private final ElasticsearchSchemaComparator comparator;
    private final ElasticsearchMigrationExecutor executor;
    private final ElasticsearchMigrationPlanLogger planLogger;

    public ElasticsearchAutoMigrationProvider(
            ElasticsearchAutoMigrationProperties properties,
            ElasticsearchOperations operations,
            ElasticsearchSchemaScanner scanner,
            ElasticsearchSchemaInspector inspector,
            ElasticsearchSchemaComparator comparator,
            ElasticsearchMigrationExecutor executor,
            ElasticsearchMigrationPlanLogger planLogger) {
        this.properties = properties;
        this.operations = operations;
        this.scanner = scanner;
        this.inspector = inspector;
        this.comparator = comparator;
        this.executor = executor;
        this.planLogger = planLogger;
    }

    @Override
    public String providerName() {
        return "elasticsearch";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public PreparedSchemaMigration prepareMigration() {
        ElasticsearchAutoMigrationMode mode = properties.getMode();
        if (mode == ElasticsearchAutoMigrationMode.NONE) {
            LOGGER.info("Elasticsearch auto migration is disabled");
            return PreparedSchemaMigration.noOp();
        }

        List<ElasticsearchIndexDefinition> desired = scanner.scan(operations);
        List<ElasticsearchIndexSnapshot> existing = inspector.inspect(operations, desired);
        ElasticsearchMigrationPlan plan = comparator.compare(desired, existing);
        return new PreparedElasticsearchMigration(mode, plan);
    }

    private final class PreparedElasticsearchMigration implements PreparedSchemaMigration {

        private final ElasticsearchAutoMigrationMode mode;
        private final ElasticsearchMigrationPlan plan;

        private PreparedElasticsearchMigration(
                ElasticsearchAutoMigrationMode mode,
                ElasticsearchMigrationPlan plan) {
            this.mode = mode;
            this.plan = plan;
        }

        @Override
        public void validate() {
            if (mode == ElasticsearchAutoMigrationMode.DRY_RUN) {
                planLogger.log(plan);
            } else {
                reportUnsupported(plan);
            }
            failIfUnsupported(plan);
        }

        @Override
        public void execute() {
            if (mode != ElasticsearchAutoMigrationMode.SAFE_UPDATE) {
                return;
            }

            failIfUnsupported(plan);
            if (plan.operations().isEmpty()) {
                LOGGER.info("Elasticsearch mappings are up to date");
                return;
            }

            for (ElasticsearchMigrationOperation operation : plan.operations()) {
                LOGGER.info("Elasticsearch migration API: {} {}", operation.method(), operation.path());
            }
            executor.execute(operations, plan.operations());
            LOGGER.info("Applied {} additive Elasticsearch migration operation(s)", plan.operations().size());
        }
    }

    private void failIfUnsupported(ElasticsearchMigrationPlan plan) {
        if (!plan.unsupportedDifferences().isEmpty()) {
            throw new IllegalStateException("Elasticsearch schema contains "
                    + plan.unsupportedDifferences().size()
                    + " unsupported difference(s); no migration was executed");
        }
    }

    private void reportUnsupported(ElasticsearchMigrationPlan plan) {
        for (ElasticsearchSchemaDifference difference : plan.unsupportedDifferences()) {
            LOGGER.warn("Unsupported Elasticsearch schema difference [{}] {}: {}",
                    difference.type(), difference.objectName(), difference.message());
        }
    }
}
