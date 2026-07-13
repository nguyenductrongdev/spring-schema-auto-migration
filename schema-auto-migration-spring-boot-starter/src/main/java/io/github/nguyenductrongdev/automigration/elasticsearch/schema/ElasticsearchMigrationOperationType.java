package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

/** Elasticsearch API operations that are safe to apply automatically. */
public enum ElasticsearchMigrationOperationType {
    CREATE_INDEX,
    PUT_MAPPING
}
