package io.github.nguyenductrongdev.automigration.elasticsearch.compare;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperationType;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifferenceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSchemaComparatorTest {

    private final ElasticsearchSchemaComparator comparator = new ElasticsearchSchemaComparator();

    @Test
    void createsMissingIndexWithSettingsAndMappings() {
        ElasticsearchIndexDefinition desired = definition(
                settings("1"),
                mapping(Map.of("id", field("keyword"))));

        ElasticsearchMigrationPlan plan = comparator.compare(
                List.of(desired),
                List.of(ElasticsearchIndexSnapshot.missing("customers")));

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo(ElasticsearchMigrationOperationType.CREATE_INDEX);
            assertThat(operation.path()).isEqualTo("/customers");
            assertThat(operation.settings()).isEqualTo(desired.settings());
            assertThat(operation.mapping()).isEqualTo(desired.mapping());
        });
    }

    @Test
    void addsMissingFieldsToAnExistingIndex() {
        ElasticsearchIndexDefinition desired = definition(
                settings("1"),
                mapping(fields(
                        "id", field("keyword"),
                        "email", field("keyword"))));
        ElasticsearchIndexSnapshot existing = snapshot(
                settings("1"),
                mapping(Map.of("id", field("keyword"))));

        ElasticsearchMigrationPlan plan = comparator.compare(List.of(desired), List.of(existing));

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo(ElasticsearchMigrationOperationType.PUT_MAPPING);
            assertThat(operation.path()).isEqualTo("/customers/_mapping");
        });
    }

    @Test
    void rejectsChangingAnExistingFieldType() {
        ElasticsearchIndexDefinition desired = definition(
                settings("1"),
                mapping(Map.of("id", field("keyword"))));
        ElasticsearchIndexSnapshot existing = snapshot(
                settings("1"),
                mapping(Map.of("id", field("text"))));

        ElasticsearchMigrationPlan plan = comparator.compare(List.of(desired), List.of(existing));

        assertThat(plan.operations()).isEmpty();
        assertThat(plan.unsupportedDifferences()).singleElement().satisfies(difference -> {
            assertThat(difference.type()).isEqualTo(ElasticsearchSchemaDifferenceType.CHANGE_FIELD_MAPPING);
            assertThat(difference.objectName()).isEqualTo("customers.id");
        });
    }

    @Test
    void alwaysReportsFieldsAbsentFromTheDesiredMapping() {
        ElasticsearchIndexDefinition desired = definition(
                settings("1"),
                mapping(Map.of("id", field("keyword"))));
        ElasticsearchIndexSnapshot existing = snapshot(
                settings("1"),
                mapping(fields(
                        "id", field("keyword"),
                        "legacy", field("text"))));

        ElasticsearchMigrationPlan plan = comparator.compare(List.of(desired), List.of(existing));

        assertThat(plan.operations()).isEmpty();
        assertThat(plan.unsupportedDifferences()).singleElement().satisfies(difference -> {
            assertThat(difference.type()).isEqualTo(ElasticsearchSchemaDifferenceType.UNMANAGED_FIELD);
            assertThat(difference.objectName()).isEqualTo("customers.legacy");
        });
    }

    @Test
    void rejectsConfiguredIndexSettingDifferencesButIgnoresServerManagedSettings() {
        ElasticsearchIndexDefinition desired = definition(
                Map.of("index", Map.of("number_of_shards", "1")),
                mapping(Map.of("id", field("keyword"))));
        ElasticsearchIndexSnapshot existing = snapshot(
                Map.of("index.number_of_shards", "2", "index.uuid", "generated"),
                mapping(Map.of("id", field("keyword"))));

        ElasticsearchMigrationPlan plan = comparator.compare(List.of(desired), List.of(existing));

        assertThat(plan.unsupportedDifferences()).singleElement().satisfies(difference -> {
            assertThat(difference.type()).isEqualTo(ElasticsearchSchemaDifferenceType.CHANGE_INDEX_SETTING);
            assertThat(difference.objectName()).isEqualTo("customers.number_of_shards");
        });
    }

    @Test
    void detectsNestedFieldAdditionsWithoutTreatingTheObjectAsChanged() {
        ElasticsearchIndexDefinition desired = definition(
                settings("1"),
                mapping(Map.of("address", objectFields(fields(
                        "city", field("text"),
                        "postalCode", field("keyword"))))));
        ElasticsearchIndexSnapshot existing = snapshot(
                settings("1"),
                mapping(Map.of("address", objectFields(Map.of("city", field("text"))))));

        ElasticsearchMigrationPlan plan = comparator.compare(List.of(desired), List.of(existing));

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.operations()).singleElement()
                .extracting(operation -> operation.type())
                .isEqualTo(ElasticsearchMigrationOperationType.PUT_MAPPING);
    }

    private ElasticsearchIndexDefinition definition(
            Map<String, Object> settings,
            Map<String, Object> mapping) {
        return new ElasticsearchIndexDefinition("customers", TestDocument.class, settings, mapping);
    }

    private ElasticsearchIndexSnapshot snapshot(
            Map<String, Object> settings,
            Map<String, Object> mapping) {
        return new ElasticsearchIndexSnapshot("customers", true, settings, mapping);
    }

    private Map<String, Object> settings(String shards) {
        return Map.of("index.number_of_shards", shards);
    }

    private Map<String, Object> mapping(Map<String, Object> properties) {
        return Map.of("properties", properties);
    }

    private Map<String, Object> field(String type) {
        return Map.of("type", type);
    }

    private Map<String, Object> objectFields(Map<String, Object> properties) {
        return Map.of("properties", properties);
    }

    private Map<String, Object> fields(
            String firstName,
            Map<String, Object> first,
            String secondName,
            Map<String, Object> second) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(firstName, first);
        result.put(secondName, second);
        return result;
    }

    private static final class TestDocument {
    }
}
