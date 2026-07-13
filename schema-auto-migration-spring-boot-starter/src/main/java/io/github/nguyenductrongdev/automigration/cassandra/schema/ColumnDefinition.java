package io.github.nguyenductrongdev.automigration.cassandra.schema;

/** A Cassandra table column represented independently from driver metadata. */
public record ColumnDefinition(String name, String cqlType, boolean primaryKey, boolean staticColumn) {

    public ColumnDefinition(String name, String cqlType, boolean primaryKey) {
        this(name, cqlType, primaryKey, false);
    }
}

