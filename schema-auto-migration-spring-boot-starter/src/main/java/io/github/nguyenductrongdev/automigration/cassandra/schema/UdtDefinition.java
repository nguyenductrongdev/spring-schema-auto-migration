package io.github.nguyenductrongdev.automigration.cassandra.schema;

import java.util.Map;

/** Desired or existing Cassandra user-defined type shape. */
public record UdtDefinition(String name, Map<String, UdtFieldDefinition> fields) {

    public UdtDefinition {
        fields = Map.copyOf(fields);
    }
}

