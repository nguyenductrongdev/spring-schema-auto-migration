package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.automigration.cassandra.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.automigration.cassandra.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.automigration.cassandra.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.automigration.cassandra.log.CassandraMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.cassandra.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifference;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifferenceType;
import io.github.nguyenductrongdev.automigration.core.PreparedSchemaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CassandraAutoMigrationProviderTest {

    @Test
    void propertiesDefaultToDisabledMode() {
        CassandraAutoMigrationProperties properties = new CassandraAutoMigrationProperties();

        assertThat(properties.getMode()).isEqualTo(CassandraAutoMigrationMode.NONE);
    }

    @Test
    void noneModeSkipsAllMigrationWork() {
        TestFixture fixture = fixture(CassandraAutoMigrationMode.NONE, emptyPlan());

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verifyNoInteractions(
                fixture.session,
                fixture.scanner,
                fixture.inspector,
                fixture.comparator,
                fixture.executor,
                fixture.planLogger);
    }

    @Test
    void dryRunLogsPlanAndNeverExecutesStatements() {
        TestFixture fixture = fixture(
                CassandraAutoMigrationMode.DRY_RUN, planWithSupportedStatement());

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verify(fixture.planLogger).log("sample", planWithSupportedStatement());
        verify(fixture.executor, never()).execute(
                fixture.session, List.of("ALTER TABLE sample ADD value text"));
    }

    @Test
    void safeUpdateExecutesOnlySupportedStatements() {
        TestFixture fixture = fixture(
                CassandraAutoMigrationMode.SAFE_UPDATE, planWithSupportedStatement());

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verify(fixture.executor).execute(
                fixture.session, List.of("ALTER TABLE sample ADD value text"));
        verifyNoInteractions(fixture.planLogger);
    }

    @Test
    void missingKeyspaceStopsBeforeSchemaScanningOrExecution() {
        TestFixture fixture = fixture(
                CassandraAutoMigrationMode.SAFE_UPDATE, planWithSupportedStatement());
        when(fixture.inspector.inspect(fixture.session, "sample"))
                .thenThrow(new IllegalStateException("Cassandra keyspace 'sample' does not exist"));

        assertThatThrownBy(fixture.provider::prepareMigration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cassandra keyspace 'sample' does not exist");

        verifyNoInteractions(fixture.scanner, fixture.comparator, fixture.executor, fixture.planLogger);
    }

    @Test
    void unsupportedDifferencesAlwaysStopBeforeAnyStatementExecutes() {
        MigrationPlan plan = new MigrationPlan(
                List.of("ALTER TABLE sample ADD value text"),
                List.of(new SchemaDifference(
                        SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                        "sample.value",
                        "Column type differs")));
        TestFixture fixture = fixture(CassandraAutoMigrationMode.SAFE_UPDATE, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();

        assertThatThrownBy(migration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration was executed");
        verifyNoInteractions(fixture.executor);
    }

    @Test
    void dryRunLogsPlanBeforeFailingOnUnsupportedDifferences() {
        MigrationPlan plan = new MigrationPlan(
                List.of("ALTER TABLE sample ADD value text"),
                List.of(new SchemaDifference(
                        SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                        "sample.value",
                        "Column type differs")));
        TestFixture fixture = fixture(CassandraAutoMigrationMode.DRY_RUN, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();

        assertThatThrownBy(migration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration was executed");

        verify(fixture.planLogger).log("sample", plan);
        verifyNoInteractions(fixture.executor);
    }

    private TestFixture fixture(
            CassandraAutoMigrationMode mode,
            MigrationPlan plan) {
        CassandraAutoMigrationProperties properties = new CassandraAutoMigrationProperties();
        properties.setMode(mode);
        properties.setKeyspaceName("sample");

        CqlSession session = mock(CqlSession.class);
        CassandraMappingContext mappingContext = mock(CassandraMappingContext.class);
        SpringDataCassandraSchemaScanner scanner = mock(SpringDataCassandraSchemaScanner.class);
        CassandraSchemaInspector inspector = mock(CassandraSchemaInspector.class);
        CassandraSchemaComparator comparator = mock(CassandraSchemaComparator.class);
        CassandraMigrationExecutor executor = mock(CassandraMigrationExecutor.class);
        CassandraMigrationPlanLogger planLogger = mock(CassandraMigrationPlanLogger.class);
        CassandraSchema schema = CassandraSchema.empty();

        when(scanner.scan(mappingContext)).thenReturn(schema);
        when(inspector.inspect(session, "sample")).thenReturn(schema);
        when(comparator.compare("sample", schema, schema)).thenReturn(plan);

        CassandraAutoMigrationProvider provider = new CassandraAutoMigrationProvider(
                properties, session, mappingContext, scanner, inspector, comparator, executor, planLogger);
        return new TestFixture(provider, session, scanner, inspector, comparator, executor, planLogger);
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
            CassandraAutoMigrationProvider provider,
            CqlSession session,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor,
            CassandraMigrationPlanLogger planLogger) {
    }
}
