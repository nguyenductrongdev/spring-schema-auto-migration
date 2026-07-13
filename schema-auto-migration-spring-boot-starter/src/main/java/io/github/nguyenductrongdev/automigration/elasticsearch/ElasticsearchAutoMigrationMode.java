package io.github.nguyenductrongdev.automigration.elasticsearch;

/** Controls Elasticsearch schema migration behavior. */
public enum ElasticsearchAutoMigrationMode {
    /** Disables Elasticsearch schema scanning and comparison. */
    NONE,
    /** Logs the ordered Elasticsearch API plan without applying it. */
    DRY_RUN,
    /** Applies only safe additive changes and rejects every unsupported difference. */
    SAFE_UPDATE
}
