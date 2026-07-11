package io.github.nguyenductrongdev.cassandra.migration.schema;

/** An unsupported schema difference that is reported but never executed. */
public record SchemaDifference(SchemaDifferenceType type, String objectName, String message) {
}

