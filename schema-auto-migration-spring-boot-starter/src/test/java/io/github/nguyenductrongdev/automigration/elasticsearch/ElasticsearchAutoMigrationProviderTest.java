package io.github.nguyenductrongdev.automigration.elasticsearch;

import io.github.nguyenductrongdev.automigration.elasticsearch.compare.ElasticsearchSchemaComparator;
import io.github.nguyenductrongdev.automigration.elasticsearch.execute.ElasticsearchMigrationExecutor;
import io.github.nguyenductrongdev.automigration.elasticsearch.inspect.ElasticsearchSchemaInspector;
import io.github.nguyenductrongdev.automigration.elasticsearch.log.ElasticsearchMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.elasticsearch.scan.ElasticsearchSchemaScanner;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifference;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchSchemaDifferenceType;
import io.github.nguyenductrongdev.automigration.core.PreparedSchemaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElasticsearchAutoMigrationProviderTest {

    @Test
    void propertiesDefaultToDisabledMode() {
        assertThat(new ElasticsearchAutoMigrationProperties().getMode())
                .isEqualTo(ElasticsearchAutoMigrationMode.NONE);
    }

    @Test
    void noneModeSkipsAllMigrationWork() {
        TestFixture fixture = fixture(ElasticsearchAutoMigrationMode.NONE, emptyPlan());

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verifyNoInteractions(
                fixture.operations,
                fixture.scanner,
                fixture.inspector,
                fixture.comparator,
                fixture.executor,
                fixture.planLogger);
    }

    @Test
    void dryRunLogsApiPlanAndNeverExecutesIt() {
        ElasticsearchMigrationPlan plan = supportedPlan();
        TestFixture fixture = fixture(ElasticsearchAutoMigrationMode.DRY_RUN, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verify(fixture.planLogger).log(plan);
        verify(fixture.executor, never()).execute(fixture.operations, plan.operations());
    }

    @Test
    void safeUpdateExecutesSupportedOperations() {
        ElasticsearchMigrationPlan plan = supportedPlan();
        TestFixture fixture = fixture(ElasticsearchAutoMigrationMode.SAFE_UPDATE, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();
        migration.validate();
        migration.execute();

        verify(fixture.executor).execute(fixture.operations, plan.operations());
        verifyNoInteractions(fixture.planLogger);
    }

    @Test
    void unsupportedDifferencesAlwaysStopBeforeAnyApiCall() {
        ElasticsearchMigrationPlan plan = new ElasticsearchMigrationPlan(
                supportedPlan().operations(),
                List.of(new ElasticsearchSchemaDifference(
                        ElasticsearchSchemaDifferenceType.CHANGE_FIELD_MAPPING,
                        "customers.email",
                        "Field type differs")));
        TestFixture fixture = fixture(ElasticsearchAutoMigrationMode.SAFE_UPDATE, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();

        assertThatThrownBy(migration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration was executed");

        verifyNoInteractions(fixture.executor);
    }

    @Test
    void dryRunLogsBeforeFailingOnUnsupportedDifferences() {
        ElasticsearchMigrationPlan plan = new ElasticsearchMigrationPlan(
                List.of(),
                List.of(new ElasticsearchSchemaDifference(
                        ElasticsearchSchemaDifferenceType.UNMANAGED_FIELD,
                        "customers.legacy",
                        "Unmanaged field")));
        TestFixture fixture = fixture(ElasticsearchAutoMigrationMode.DRY_RUN, plan);

        PreparedSchemaMigration migration = fixture.provider.prepareMigration();

        assertThatThrownBy(migration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration was executed");

        verify(fixture.planLogger).log(plan);
        verifyNoInteractions(fixture.executor);
    }

    private TestFixture fixture(
            ElasticsearchAutoMigrationMode mode,
            ElasticsearchMigrationPlan plan) {
        ElasticsearchAutoMigrationProperties properties = new ElasticsearchAutoMigrationProperties();
        properties.setMode(mode);
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        ElasticsearchSchemaScanner scanner = mock(ElasticsearchSchemaScanner.class);
        ElasticsearchSchemaInspector inspector = mock(ElasticsearchSchemaInspector.class);
        ElasticsearchSchemaComparator comparator = mock(ElasticsearchSchemaComparator.class);
        ElasticsearchMigrationExecutor executor = mock(ElasticsearchMigrationExecutor.class);
        ElasticsearchMigrationPlanLogger planLogger = mock(ElasticsearchMigrationPlanLogger.class);

        List<ElasticsearchIndexDefinition> desired = List.of(definition());
        List<ElasticsearchIndexSnapshot> existing =
                List.of(ElasticsearchIndexSnapshot.missing("customers"));
        when(scanner.scan(operations)).thenReturn(desired);
        when(inspector.inspect(operations, desired)).thenReturn(existing);
        when(comparator.compare(desired, existing)).thenReturn(plan);

        ElasticsearchAutoMigrationProvider provider = new ElasticsearchAutoMigrationProvider(
                properties, operations, scanner, inspector, comparator, executor, planLogger);
        return new TestFixture(
                provider, operations, scanner, inspector, comparator, executor, planLogger);
    }

    private ElasticsearchMigrationPlan emptyPlan() {
        return new ElasticsearchMigrationPlan(List.of(), List.of());
    }

    private ElasticsearchMigrationPlan supportedPlan() {
        return new ElasticsearchMigrationPlan(
                List.of(ElasticsearchMigrationOperation.createIndex(definition())),
                List.of());
    }

    private ElasticsearchIndexDefinition definition() {
        return new ElasticsearchIndexDefinition(
                "customers",
                TestDocument.class,
                Map.of("index.number_of_shards", "1"),
                Map.of("properties", Map.of("id", Map.of("type", "keyword"))));
    }

    private record TestFixture(
            ElasticsearchAutoMigrationProvider provider,
            ElasticsearchOperations operations,
            ElasticsearchSchemaScanner scanner,
            ElasticsearchSchemaInspector inspector,
            ElasticsearchSchemaComparator comparator,
            ElasticsearchMigrationExecutor executor,
            ElasticsearchMigrationPlanLogger planLogger) {
    }

    private static final class TestDocument {
    }
}
