package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

import java.util.Objects;

/** One unsupported difference between desired and existing Elasticsearch schemas. */
public record ElasticsearchSchemaDifference(
        ElasticsearchSchemaDifferenceType type,
        String objectName,
        String message) {

    public ElasticsearchSchemaDifference {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(objectName, "objectName must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
