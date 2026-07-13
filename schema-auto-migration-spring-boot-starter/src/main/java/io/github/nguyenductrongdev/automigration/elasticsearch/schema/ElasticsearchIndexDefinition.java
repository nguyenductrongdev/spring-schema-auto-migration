package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Desired Elasticsearch index settings and mappings derived from one Spring Data entity. */
public record ElasticsearchIndexDefinition(
        String indexName,
        Class<?> entityType,
        Map<String, Object> settings,
        Map<String, Object> mapping) {

    public ElasticsearchIndexDefinition {
        Objects.requireNonNull(indexName, "indexName must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        settings = immutableCopy(settings, "settings");
        mapping = immutableCopy(mapping, "mapping");
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
