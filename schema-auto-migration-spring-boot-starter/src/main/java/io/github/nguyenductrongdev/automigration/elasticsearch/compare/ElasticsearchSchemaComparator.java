package io.github.nguyenductrongdev.automigration.elasticsearch.compare;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifference;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifferenceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Produces additive Elasticsearch operations and reports every destructive or ambiguous difference. */
public class ElasticsearchSchemaComparator {

    public ElasticsearchMigrationPlan compare(
            List<ElasticsearchIndexDefinition> desiredIndexes,
            List<ElasticsearchIndexSnapshot> existingIndexes) {
        Map<String, ElasticsearchIndexSnapshot> existingByName = new LinkedHashMap<>();
        for (ElasticsearchIndexSnapshot existing : existingIndexes) {
            if (existingByName.put(existing.indexName(), existing) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Elasticsearch snapshot for index '" + existing.indexName() + "'");
            }
        }

        List<ElasticsearchMigrationOperation> operations = new ArrayList<>();
        List<ElasticsearchSchemaDifference> differences = new ArrayList<>();
        List<ElasticsearchIndexDefinition> orderedDesired = desiredIndexes.stream()
                .sorted(Comparator.comparing(ElasticsearchIndexDefinition::indexName))
                .toList();

        for (ElasticsearchIndexDefinition desired : orderedDesired) {
            ElasticsearchIndexSnapshot existing = existingByName.get(desired.indexName());
            if (existing == null) {
                throw new IllegalArgumentException(
                        "No Elasticsearch snapshot was supplied for index '" + desired.indexName() + "'");
            }
            if (!existing.exists()) {
                operations.add(ElasticsearchMigrationOperation.createIndex(desired));
                continue;
            }

            boolean[] missingField = {false};
            compareMappingNode(
                    desired.indexName(),
                    desired.mapping(),
                    existing.mapping(),
                    missingField,
                    differences);
            compareSettings(desired, existing, differences);
            if (missingField[0]) {
                operations.add(ElasticsearchMigrationOperation.putMapping(desired));
            }
        }

        return new ElasticsearchMigrationPlan(operations, differences);
    }

    private void compareMappingNode(
            String path,
            Map<String, Object> desired,
            Map<String, Object> existing,
            boolean[] missingField,
            List<ElasticsearchSchemaDifference> differences) {
        Map<String, Object> desiredParameters = withoutProperties(desired);
        Map<String, Object> existingParameters = withoutProperties(existing);
        if (!normalize(desiredParameters).equals(normalize(existingParameters))) {
            differences.add(new ElasticsearchSchemaDifference(
                    ElasticsearchSchemaDifferenceType.CHANGE_FIELD_MAPPING,
                    path,
                    "Mapping parameters differ; desired=" + desiredParameters
                            + ", existing=" + existingParameters));
        }

        Map<String, Object> desiredProperties = asMap(desired.get("properties"));
        Map<String, Object> existingProperties = asMap(existing.get("properties"));
        TreeSet<String> fieldNames = new TreeSet<>();
        fieldNames.addAll(desiredProperties.keySet());
        fieldNames.addAll(existingProperties.keySet());

        for (String fieldName : fieldNames) {
            String fieldPath = path + "." + fieldName;
            boolean desiredContains = desiredProperties.containsKey(fieldName);
            boolean existingContains = existingProperties.containsKey(fieldName);
            if (desiredContains && !existingContains) {
                missingField[0] = true;
                continue;
            }
            if (!desiredContains) {
                differences.add(new ElasticsearchSchemaDifference(
                        ElasticsearchSchemaDifferenceType.UNMANAGED_FIELD,
                        fieldPath,
                        "Field exists in the managed index but is absent from the desired mapping"));
                continue;
            }
            compareMappingNode(
                    fieldPath,
                    asMap(desiredProperties.get(fieldName)),
                    asMap(existingProperties.get(fieldName)),
                    missingField,
                    differences);
        }
    }

    private void compareSettings(
            ElasticsearchIndexDefinition desired,
            ElasticsearchIndexSnapshot existing,
            List<ElasticsearchSchemaDifference> differences) {
        Map<String, String> desiredSettings = flattenSettings(desired.settings());
        Map<String, String> existingSettings = flattenSettings(existing.settings());
        for (Map.Entry<String, String> setting : desiredSettings.entrySet()) {
            String existingValue = existingSettings.get(setting.getKey());
            if (!Objects.equals(setting.getValue(), existingValue)) {
                differences.add(new ElasticsearchSchemaDifference(
                        ElasticsearchSchemaDifferenceType.CHANGE_INDEX_SETTING,
                        desired.indexName() + "." + setting.getKey(),
                        "Index setting differs; desired=" + setting.getValue()
                                + ", existing=" + existingValue));
            }
        }
    }

    private Map<String, Object> withoutProperties(Map<String, Object> mapping) {
        Map<String, Object> result = new LinkedHashMap<>(mapping);
        result.remove("properties");
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected Elasticsearch mapping object but found " + value);
        }
        return (Map<String, Object>) map;
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> normalized.put(
                            String.valueOf(entry.getKey()),
                            normalize(entry.getValue())));
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalize).toList();
        }
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, String> flattenSettings(Map<String, Object> settings) {
        Map<String, String> flattened = new LinkedHashMap<>();
        flattenSettings("", settings, flattened);
        Map<String, String> normalized = new LinkedHashMap<>();
        flattened.forEach((key, value) -> normalized.put(stripIndexPrefix(key), value));
        return normalized;
    }

    private void flattenSettings(String prefix, Map<?, ?> values, Map<String, String> target) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = prefix.isEmpty()
                    ? String.valueOf(entry.getKey())
                    : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flattenSettings(key, nested, target);
            } else {
                target.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    private String stripIndexPrefix(String key) {
        return key.startsWith("index.") ? key.substring("index.".length()) : key;
    }
}
