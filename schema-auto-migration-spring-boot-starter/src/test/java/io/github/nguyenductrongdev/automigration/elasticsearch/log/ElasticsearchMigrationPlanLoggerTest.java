package io.github.nguyenductrongdev.automigration.elasticsearch.log;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifference;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ElasticsearchMigrationPlanLoggerTest {

    @Test
    void logsApiMethodPathBodyAndUnsupportedDifferences(CapturedOutput output) {
        ElasticsearchIndexDefinition definition = new ElasticsearchIndexDefinition(
                "customers",
                TestDocument.class,
                Map.of("index.number_of_shards", "1"),
                Map.of("properties", Map.of("id", Map.of("type", "keyword"))));
        ElasticsearchMigrationPlan plan = new ElasticsearchMigrationPlan(
                List.of(ElasticsearchMigrationOperation.createIndex(definition)),
                List.of(new ElasticsearchSchemaDifference(
                        ElasticsearchSchemaDifferenceType.UNMANAGED_FIELD,
                        "customers.legacy",
                        "Field is not managed")));

        new ElasticsearchMigrationPlanLogger().log(plan);

        assertThat(output.getAll())
                .contains("[ELASTICSEARCH][DRY_RUN] Planned operations: 1; unsupported differences: 1")
                .contains("[ELASTICSEARCH][DRY_RUN][API][1/1] PUT /customers body={")
                .contains("\"mappings\"")
                .contains("\"properties\"")
                .doesNotContain("[ELASTICSEARCH][DRY_RUN][BODY]")
                .contains("[ELASTICSEARCH][DRY_RUN][UNSUPPORTED][1/1] [UNMANAGED_FIELD] "
                        + "customers.legacy: Field is not managed")
                .contains("[ELASTICSEARCH][DRY_RUN] No migration was executed");
    }

    private static final class TestDocument {
    }
}
