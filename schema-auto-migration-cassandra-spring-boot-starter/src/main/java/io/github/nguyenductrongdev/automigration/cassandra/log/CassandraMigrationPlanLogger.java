package io.github.nguyenductrongdev.automigration.cassandra.log;

import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Logs a Cassandra dry-run plan in deterministic execution order. */
public class CassandraMigrationPlanLogger {

    public static final String LOGGER_NAME = "io.github.nguyenductrongdev.automigration.plan.cassandra";

    private static final Logger PLAN_LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    public void log(String keyspace, MigrationPlan plan) {
        Objects.requireNonNull(keyspace, "keyspace must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        PLAN_LOGGER.info(
                "[CASSANDRA][DRY_RUN] Keyspace: {}; planned operations: {}; unsupported differences: {}",
                singleLine(keyspace),
                plan.supportedCql().size(),
                plan.unsupportedDifferences().size());

        if (plan.supportedCql().isEmpty()) {
            PLAN_LOGGER.info("[CASSANDRA][DRY_RUN][CQL] No operations are required");
        } else {
            for (int index = 0; index < plan.supportedCql().size(); index++) {
                PLAN_LOGGER.info(
                        "[CASSANDRA][DRY_RUN][CQL][{}/{}] {}",
                        index + 1,
                        plan.supportedCql().size(),
                        statementForLog(plan.supportedCql().get(index)));
            }
        }

        for (int index = 0; index < plan.unsupportedDifferences().size(); index++) {
            SchemaDifference difference = plan.unsupportedDifferences().get(index);
            PLAN_LOGGER.warn(
                    "[CASSANDRA][DRY_RUN][UNSUPPORTED][{}/{}] [{}] {}: {}",
                    index + 1,
                    plan.unsupportedDifferences().size(),
                    difference.type(),
                    singleLine(difference.objectName()),
                    singleLine(difference.message()));
        }

        PLAN_LOGGER.info("[CASSANDRA][DRY_RUN] No migration was executed");
    }

    private String statementForLog(String statement) {
        String normalized = singleLine(statement).stripTrailing();
        return normalized.endsWith(";") ? normalized : normalized + ";";
    }

    private String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
