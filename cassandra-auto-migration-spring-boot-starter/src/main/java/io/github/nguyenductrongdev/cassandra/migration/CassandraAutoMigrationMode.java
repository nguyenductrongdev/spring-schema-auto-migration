package io.github.nguyenductrongdev.cassandra.migration;

/** Controls how detected schema differences are handled at application startup. */
public enum CassandraAutoMigrationMode {
    /** Disables schema scanning and comparison. */
    NONE,
    /** Reports all differences without producing or executing migrations. */
    VALIDATE,
    /** Reports supported CQL statements without executing them. */
    SCRIPT,
    /** Executes supported additive CQL statements. */
    UPDATE
}

