package io.github.nguyenductrongdev.automigration.elasticsearch.schema;

import java.util.List;
import java.util.Objects;

/** Ordered safe operations and unsupported Elasticsearch differences. */
public record ElasticsearchMigrationPlan(
        List<ElasticsearchMigrationOperation> operations,
        List<ElasticsearchSchemaDifference> unsupportedDifferences) {

    public ElasticsearchMigrationPlan {
        Objects.requireNonNull(operations, "operations must not be null");
        Objects.requireNonNull(unsupportedDifferences, "unsupportedDifferences must not be null");
        operations = List.copyOf(operations);
        unsupportedDifferences = List.copyOf(unsupportedDifferences);
    }

    public boolean isEmpty() {
        return operations.isEmpty() && unsupportedDifferences.isEmpty();
    }
}
