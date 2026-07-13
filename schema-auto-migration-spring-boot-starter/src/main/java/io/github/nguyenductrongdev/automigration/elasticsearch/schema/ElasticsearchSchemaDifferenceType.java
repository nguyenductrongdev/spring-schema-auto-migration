package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

/** Elasticsearch differences that SAFE_UPDATE deliberately refuses to apply. */
public enum ElasticsearchSchemaDifferenceType {
    UNMANAGED_FIELD,
    CHANGE_FIELD_MAPPING,
    CHANGE_INDEX_SETTING
}
