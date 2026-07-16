package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CassandraMigrationSessionTest {

    @Test
    void nonOwningWrapperLeavesSessionOpen() {
        CqlSession session = mock(CqlSession.class);
        CassandraMigrationSession migrationSession = CassandraMigrationSession.of(session);

        assertThat(migrationSession.cqlSession()).isSameAs(session);
        migrationSession.close();

        verify(session, never()).close();
    }

    @Test
    void ownedWrapperClosesSession() {
        CqlSession session = mock(CqlSession.class);
        CassandraMigrationSession migrationSession = CassandraMigrationSession.owned(session);

        assertThat(migrationSession.cqlSession()).isSameAs(session);
        migrationSession.close();

        verify(session).close();
    }

    @Test
    void springClosesOwnedSessionWithApplicationContext() {
        CqlSession session = mock(CqlSession.class);

        new ApplicationContextRunner()
                .withBean(CassandraMigrationSession.class,
                        () -> CassandraMigrationSession.owned(session))
                .run(context -> assertThat(context).hasSingleBean(CassandraMigrationSession.class));

        verify(session).close();
    }
}