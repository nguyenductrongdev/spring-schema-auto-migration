package io.github.nguyenductrongdev.automigration.cassandra.schema;

import java.util.Map;

/** A snapshot of Cassandra tables and user-defined types. */
public record CassandraSchema(Map<String, TableDefinition> tables, Map<String, UdtDefinition> udts) {

    public CassandraSchema {
        tables = Map.copyOf(tables);
        udts = Map.copyOf(udts);
    }

    public static CassandraSchema empty() {
        return new CassandraSchema(Map.of(), Map.of());
    }
}

