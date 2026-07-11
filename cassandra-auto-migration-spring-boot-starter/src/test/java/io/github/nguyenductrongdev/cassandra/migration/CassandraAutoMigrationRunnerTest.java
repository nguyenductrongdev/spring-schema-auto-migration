package io.github.nguyenductrongdev.cassandra.migration;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.cassandra.migration.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.cassandra.migration.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.cassandra.migration.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.cassandra.migration.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.cassandra.migration.schema.CassandraSchema;
import io.github.nguyenductrongdev.cassandra.migration.schema.MigrationPlan;
import io.github.nguyenductrongdev.cassandra.migration.schema.SchemaDifference;
import io.github.nguyenductrongdev.cassandra.migration.schema.SchemaDifferenceType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CassandraAutoMigrationRunnerTest {

    @Test
    void noneModeSkipsAllMigrationWork() {
        TestFixture fixture = fixture(CassandraAutoMigrationMode.NONE, false, emptyPlan());

        fixture.runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(fixture.scanner, fixture.inspector, fixture.comparator, fixture.executor);
    }

    @Test
    void validateAndScriptModesNeverExecuteStatements() {
        TestFixture validate = fixture(
                CassandraAutoMigrationMode.VALIDATE, false, planWithSupportedStatement());
        TestFixture script = fixture(
                CassandraAutoMigrationMode.SCRIPT, false, planWithSupportedStatement());

        validate.runner.run(new DefaultApplicationArguments());
        script.runner.run(new DefaultApplicationArguments());

        verify(validate.executor, never()).execute(validate.session, List.of("ALTER TABLE sample ADD value text"));
        verify(script.executor, never()).execute(script.session, List.of("ALTER TABLE sample ADD value text"));
    }

    @Test
    void updateModeExecutesOnlySupportedStatements() {
        TestFixture fixture = fixture(
                CassandraAutoMigrationMode.UPDATE, false, planWithSupportedStatement());

        fixture.runner.run(new DefaultApplicationArguments());

        verify(fixture.executor).execute(
                fixture.session, List.of("ALTER TABLE sample ADD value text"));
    }

    @Test
    void failOnUnsupportedStopsBeforeAnyStatementExecutes() {
        MigrationPlan plan = new MigrationPlan(
                List.of("ALTER TABLE sample ADD value text"),
                List.of(new SchemaDifference(
                        SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                        "sample.value",
                        "Column type differs")));
        TestFixture fixture = fixture(CassandraAutoMigrationMode.UPDATE, true, plan);

        assertThatThrownBy(() -> fixture.runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration was executed");
        verifyNoInteractions(fixture.executor);
    }

    private TestFixture fixture(
            CassandraAutoMigrationMode mode,
            boolean failOnUnsupported,
            MigrationPlan plan) {
        CassandraAutoMigrationProperties properties = new CassandraAutoMigrationProperties();
        properties.setMode(mode);
        properties.setKeyspaceName("sample");
        properties.setFailOnUnsupported(failOnUnsupported);
        properties.setReportUnmanagedObjects(false);

        CqlSession session = mock(CqlSession.class);
        CassandraMappingContext mappingContext = mock(CassandraMappingContext.class);
        SpringDataCassandraSchemaScanner scanner = mock(SpringDataCassandraSchemaScanner.class);
        CassandraSchemaInspector inspector = mock(CassandraSchemaInspector.class);
        CassandraSchemaComparator comparator = mock(CassandraSchemaComparator.class);
        CassandraMigrationExecutor executor = mock(CassandraMigrationExecutor.class);
        CassandraSchema schema = CassandraSchema.empty();

        when(scanner.scan(mappingContext)).thenReturn(schema);
        when(inspector.inspect(session, "sample")).thenReturn(schema);
        when(comparator.compare("sample", schema, schema, false)).thenReturn(plan);

        CassandraAutoMigrationRunner runner = new CassandraAutoMigrationRunner(
                properties, session, mappingContext, scanner, inspector, comparator, executor);
        return new TestFixture(runner, session, scanner, inspector, comparator, executor);
    }

    private static MigrationPlan emptyPlan() {
        return new MigrationPlan(List.of(), List.of());
    }

    private static MigrationPlan planWithSupportedStatement() {
        return new MigrationPlan(
                List.of("ALTER TABLE sample ADD value text"),
                List.of());
    }

    private record TestFixture(
            CassandraAutoMigrationRunner runner,
            CqlSession session,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor) {
    }
}
