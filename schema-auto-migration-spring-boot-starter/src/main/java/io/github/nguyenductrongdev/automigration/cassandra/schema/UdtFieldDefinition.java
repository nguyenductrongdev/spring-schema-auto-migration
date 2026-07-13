package io.github.nguyenductrongdev.automigration.cassandra.schema;

/** A field in a Cassandra user-defined type. */
public record UdtFieldDefinition(String name, String cqlType) {
}

