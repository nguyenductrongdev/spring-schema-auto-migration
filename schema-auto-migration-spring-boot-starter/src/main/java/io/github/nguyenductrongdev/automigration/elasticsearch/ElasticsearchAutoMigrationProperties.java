package io.github.nguyenductrongdev.automigration.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Elasticsearch auto migration. */
@ConfigurationProperties("elasticsearch.auto-migration")
public class ElasticsearchAutoMigrationProperties {

    private ElasticsearchAutoMigrationMode mode = ElasticsearchAutoMigrationMode.NONE;

    public ElasticsearchAutoMigrationMode getMode() {
        return mode;
    }

    public void setMode(ElasticsearchAutoMigrationMode mode) {
        this.mode = mode;
    }
}
