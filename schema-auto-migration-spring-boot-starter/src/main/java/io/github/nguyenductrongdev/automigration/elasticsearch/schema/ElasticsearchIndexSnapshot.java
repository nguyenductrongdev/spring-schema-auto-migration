package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Current Elasticsearch index state inspected from the cluster. */
public record ElasticsearchIndexSnapshot(
        String indexName,
        boolean exists,
        Map<String, Object> settings,
        Map<String, Object> mapping) {

    public ElasticsearchIndexSnapshot {
        Objects.requireNonNull(indexName, "indexName must not be null");
        settings = immutableCopy(settings, "settings");
        mapping = immutableCopy(mapping, "mapping");
    }

    public static ElasticsearchIndexSnapshot missing(String indexName) {
        return new ElasticsearchIndexSnapshot(indexName, false, Map.of(), Map.of());
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
