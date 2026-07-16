package io.github.nguyenductrongdev.automigration.cassandra.execute;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CassandraMigrationExecutorTest {

    private final CassandraMigrationExecutor executor = new CassandraMigrationExecutor();

    @Test
    void executesStatementsSequentiallyWhenSchemaIsInAgreement() {
        CqlSession session = mock(CqlSession.class);
        ResultSet firstResult = resultWithSchemaAgreement(true);
        ResultSet secondResult = resultWithSchemaAgreement(true);
        when(session.execute("FIRST")).thenReturn(firstResult);
        when(session.execute("SECOND")).thenReturn(secondResult);

        executor.execute(session, List.of("FIRST", "SECOND"));

        InOrder order = inOrder(session);
        order.verify(session).execute("FIRST");
        order.verify(session).execute("SECOND");
    }

    @Test
    void stopsWhenSchemaAgreementIsNotReached() {
        CqlSession session = mock(CqlSession.class);
        ResultSet firstResult = resultWithSchemaAgreement(false);
        when(session.execute("FIRST")).thenReturn(firstResult);

        assertThatThrownBy(() -> executor.execute(session, List.of("FIRST", "SECOND")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("statement 1 of 2");

        verify(session, never()).execute("SECOND");
    }

    private ResultSet resultWithSchemaAgreement(boolean schemaInAgreement) {
        ResultSet result = mock(ResultSet.class);
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        when(result.getExecutionInfo()).thenReturn(executionInfo);
        when(executionInfo.isSchemaInAgreement()).thenReturn(schemaInAgreement);
        return result;
    }
}