package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One ordered Elasticsearch REST operation in a migration plan. */
public record ElasticsearchMigrationOperation(
        ElasticsearchMigrationOperationType type,
        String indexName,
        Class<?> entityType,
        Map<String, Object> settings,
        Map<String, Object> mapping) {

    public ElasticsearchMigrationOperation {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(indexName, "indexName must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        settings = immutableCopy(settings, "settings");
        mapping = immutableCopy(mapping, "mapping");
    }

    public static ElasticsearchMigrationOperation createIndex(ElasticsearchIndexDefinition definition) {
        return new ElasticsearchMigrationOperation(
                ElasticsearchMigrationOperationType.CREATE_INDEX,
                definition.indexName(),
                definition.entityType(),
                definition.settings(),
                definition.mapping());
    }

    public static ElasticsearchMigrationOperation putMapping(ElasticsearchIndexDefinition definition) {
        return new ElasticsearchMigrationOperation(
                ElasticsearchMigrationOperationType.PUT_MAPPING,
                definition.indexName(),
                definition.entityType(),
                Map.of(),
                definition.mapping());
    }

    public String method() {
        return "PUT";
    }

    public String path() {
        return type == ElasticsearchMigrationOperationType.CREATE_INDEX
                ? "/" + indexName
                : "/" + indexName + "/_mapping";
    }

    public Map<String, Object> body() {
        if (type == ElasticsearchMigrationOperationType.PUT_MAPPING) {
            return mapping;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (!settings.isEmpty()) {
            body.put("settings", settings);
        }
        body.put("mappings", mapping);
        return Collections.unmodifiableMap(body);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
