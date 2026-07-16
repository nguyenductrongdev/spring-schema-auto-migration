package io.github.nguyenductrongdev.automigration.cassandra.schema;

/** An unsupported schema difference that is reported but never executed. */
public record SchemaDifference(SchemaDifferenceType type, String objectName, String message) {
}

