package io.github.nguyenductrongdev.cassandra.migration.schema;

/** A field in a Cassandra user-defined type. */
public record UdtFieldDefinition(String name, String cqlType) {
}

