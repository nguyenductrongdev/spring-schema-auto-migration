package io.github.nguyenductrongdev.automigration.cassandra;

/** Controls how detected schema differences are handled at application startup. */
public enum CassandraAutoMigrationMode {
    /** Disables schema scanning and comparison. */
    NONE,
    /** Logs the ordered migration plan without executing it. */
    DRY_RUN,
    /** Executes supported additive changes only after verifying that the keyspace exists. */
    SAFE_UPDATE
}
