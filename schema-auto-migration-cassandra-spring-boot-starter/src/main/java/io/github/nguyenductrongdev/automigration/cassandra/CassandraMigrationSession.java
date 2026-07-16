package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;

import java.util.Objects;

/**
 * Supplies an optional dedicated Cassandra session for schema changes.
 *
 * <p>Applications that need separate DDL credentials can expose this interface as a bean. When no
 * bean is present, the starter uses Spring Boot's application {@link CqlSession}.
 */
@FunctionalInterface
public interface CassandraMigrationSession extends AutoCloseable {

    /** Returns the session used for schema inspection and DDL execution. */
    CqlSession cqlSession();

    /** Leaves lifecycle ownership with the caller. */
    @Override
    default void close() {
    }

    /** Wraps an existing session without transferring lifecycle ownership. */
    static CassandraMigrationSession of(CqlSession session) {
        CqlSession requiredSession = Objects.requireNonNull(session, "session must not be null");
        return () -> requiredSession;
    }

    /** Wraps a dedicated session and closes it with the Spring application context. */
    static CassandraMigrationSession owned(CqlSession session) {
        CqlSession requiredSession = Objects.requireNonNull(session, "session must not be null");
        return new CassandraMigrationSession() {
            @Override
            public CqlSession cqlSession() {
                return requiredSession;
            }

            @Override
            public void close() {
                requiredSession.close();
            }
        };
    }
}