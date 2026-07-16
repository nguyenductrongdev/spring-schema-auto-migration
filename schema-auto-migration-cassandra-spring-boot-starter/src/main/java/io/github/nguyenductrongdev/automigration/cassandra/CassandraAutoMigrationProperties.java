package io.github.nguyenductrongdev.automigration.cassandra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Cassandra auto migration. */
@ConfigurationProperties("schema-auto-migration.cassandra")
public class CassandraAutoMigrationProperties {

    /** Migration behavior applied during application startup. */
    private CassandraAutoMigrationMode mode = CassandraAutoMigrationMode.NONE;

    public CassandraAutoMigrationMode getMode() {
        return mode;
    }

    public void setMode(CassandraAutoMigrationMode mode) {
        this.mode = mode;
    }
}