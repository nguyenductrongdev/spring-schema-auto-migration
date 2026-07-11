package io.github.nguyenductrongdev.cassandra.migration.schema;

import java.util.List;

/** Supported additive statements and unsupported differences for one comparison. */
public record MigrationPlan(List<String> supportedCql, List<SchemaDifference> unsupportedDifferences) {

    public MigrationPlan {
        supportedCql = List.copyOf(supportedCql);
        unsupportedDifferences = List.copyOf(unsupportedDifferences);
    }

    public boolean isEmpty() {
        return supportedCql.isEmpty() && unsupportedDifferences.isEmpty();
    }
}
