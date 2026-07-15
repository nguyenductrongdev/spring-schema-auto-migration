package io.github.nguyenductrongdev.automigration.elasticsearch.log;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.document.Document;

import java.util.Objects;

/** Logs an Elasticsearch dry-run plan in deterministic execution order. */
public class ElasticsearchMigrationPlanLogger {

    public static final String LOGGER_NAME = "io.github.nguyenductrongdev.automigration.plan.elasticsearch";

    private static final Logger PLAN_LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    public void log(ElasticsearchMigrationPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        PLAN_LOGGER.info(
                "[ELASTICSEARCH][DRY_RUN] Planned operations: {}; unsupported differences: {}",
                plan.operations().size(),
                plan.unsupportedDifferences().size());

        if (plan.operations().isEmpty()) {
            PLAN_LOGGER.info("[ELASTICSEARCH][DRY_RUN][API] No operations are required");
        } else {
            for (int index = 0; index < plan.operations().size(); index++) {
                ElasticsearchMigrationOperation operation = plan.operations().get(index);
                PLAN_LOGGER.info(
                        "[ELASTICSEARCH][DRY_RUN][API][{}/{}] {} {} body={}",
                        index + 1,
                        plan.operations().size(),
                        operation.method(),
                        singleLine(operation.path()),
                        singleLine(Document.from(operation.body()).toJson()));
            }
        }

        for (int index = 0; index < plan.unsupportedDifferences().size(); index++) {
            ElasticsearchSchemaDifference difference = plan.unsupportedDifferences().get(index);
            PLAN_LOGGER.warn(
                    "[ELASTICSEARCH][DRY_RUN][UNSUPPORTED][{}/{}] [{}] {}: {}",
                    index + 1,
                    plan.unsupportedDifferences().size(),
                    difference.type(),
                    singleLine(difference.objectName()),
                    singleLine(difference.message()));
        }

        PLAN_LOGGER.info("[ELASTICSEARCH][DRY_RUN] No migration was executed");
    }

    private String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
