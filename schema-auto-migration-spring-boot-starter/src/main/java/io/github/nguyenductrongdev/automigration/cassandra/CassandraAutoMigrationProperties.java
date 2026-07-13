package io.github.nguyenductrongdev.automigration.cassandra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Cassandra auto migration. */
@ConfigurationProperties("cassandra.auto-migration")
public class CassandraAutoMigrationProperties {

    private CassandraAutoMigrationMode mode = CassandraAutoMigrationMode.NONE;
    private String keyspaceName;

    public CassandraAutoMigrationMode getMode() {
        return mode;
    }

    public void setMode(CassandraAutoMigrationMode mode) {
        this.mode = mode;
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    public void setKeyspaceName(String keyspaceName) {
        this.keyspaceName = keyspaceName;
    }

}
