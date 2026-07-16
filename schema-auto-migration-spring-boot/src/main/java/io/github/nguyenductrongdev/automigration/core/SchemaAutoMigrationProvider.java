package io.github.nguyenductrongdev.automigration.core;

import org.springframework.core.Ordered;

/** A database-specific schema migration provider executed by the shared startup coordinator. */
public interface SchemaAutoMigrationProvider extends Ordered {

    /** Stable provider name used in coordinator logs. */
    String providerName();

    /** Inspects the database and builds an immutable migration ready for global validation. */
    PreparedSchemaMigration prepareMigration();

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
