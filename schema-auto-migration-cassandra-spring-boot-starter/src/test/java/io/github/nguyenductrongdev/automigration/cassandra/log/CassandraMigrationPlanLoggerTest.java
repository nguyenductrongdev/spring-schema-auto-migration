package io.github.nguyenductrongdev.automigration.cassandra.log;

import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifference;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CassandraMigrationPlanLoggerTest {

    @Test
    void logsCqlAndUnsupportedDifferencesInPlanOrder(CapturedOutput output) {
        String createType = "CREATE TYPE sample.address (city text)";
        String createTable = "CREATE TABLE sample.customer (id uuid PRIMARY KEY)";
        MigrationPlan plan = new MigrationPlan(
                List.of(createType, createTable),
                List.of(new SchemaDifference(
                        SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                        "customer.legacy_value",
                        "Column type differs")));
        CassandraMigrationPlanLogger planLogger = new CassandraMigrationPlanLogger();

        planLogger.log("sample", plan);

        String logs = output.getAll();
        assertThat(logs)
                .contains("[CASSANDRA][DRY_RUN] Keyspace: sample; planned operations: 2; unsupported differences: 1")
                .contains("[CASSANDRA][DRY_RUN][CQL][1/2] " + createType + ";")
                .contains("[CASSANDRA][DRY_RUN][CQL][2/2] " + createTable + ";")
                .contains("[CASSANDRA][DRY_RUN][UNSUPPORTED][1/1] [CHANGE_COLUMN_TYPE] "
                        + "customer.legacy_value: Column type differs")
                .contains("[CASSANDRA][DRY_RUN] No migration was executed");
        assertThat(logs.indexOf(createType)).isLessThan(logs.indexOf(createTable));
    }

    @Test
    void logsWhenNoOperationsAreRequired(CapturedOutput output) {
        CassandraMigrationPlanLogger planLogger = new CassandraMigrationPlanLogger();

        planLogger.log("sample", new MigrationPlan(List.of(), List.of()));

        assertThat(output.getAll())
                .contains("[CASSANDRA][DRY_RUN][CQL] No operations are required")
                .contains("[CASSANDRA][DRY_RUN] No migration was executed");
    }
}
