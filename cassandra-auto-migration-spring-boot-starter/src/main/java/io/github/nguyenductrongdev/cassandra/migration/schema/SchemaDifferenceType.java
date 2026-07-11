package io.github.nguyenductrongdev.cassandra.migration.schema;

/** Categories that require a destructive or ambiguous migration. */
public enum SchemaDifferenceType {
    DROP_TABLE,
    DROP_COLUMN,
    DROP_UDT,
    DROP_UDT_FIELD,
    CHANGE_COLUMN_TYPE,
    CHANGE_COLUMN_KIND,
    CHANGE_CLUSTERING_ORDER,
    CHANGE_UDT_FIELD_TYPE,
    MODIFY_PRIMARY_KEY
}
