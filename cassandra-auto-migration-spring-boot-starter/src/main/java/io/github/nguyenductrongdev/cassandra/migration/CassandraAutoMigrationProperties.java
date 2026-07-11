package io.github.nguyenductrongdev.cassandra.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Cassandra auto migration. */
@ConfigurationProperties("cassandra.auto-migration")
public class CassandraAutoMigrationProperties {

    private CassandraAutoMigrationMode mode = CassandraAutoMigrationMode.UPDATE;
    private String keyspaceName;
    private boolean failOnUnsupported;
    private boolean reportUnmanagedObjects = true;

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

    public boolean isFailOnUnsupported() {
        return failOnUnsupported;
    }

    public void setFailOnUnsupported(boolean failOnUnsupported) {
        this.failOnUnsupported = failOnUnsupported;
    }

    public boolean isReportUnmanagedObjects() {
        return reportUnmanagedObjects;
    }

    public void setReportUnmanagedObjects(boolean reportUnmanagedObjects) {
        this.reportUnmanagedObjects = reportUnmanagedObjects;
    }
}

