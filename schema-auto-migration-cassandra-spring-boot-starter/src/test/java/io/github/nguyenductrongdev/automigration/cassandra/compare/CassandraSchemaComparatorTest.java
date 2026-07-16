package io.github.nguyenductrongdev.automigration.cassandra.compare;

import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifferenceType;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CassandraSchemaComparatorTest {

    private final CassandraSchemaComparator comparator = new CassandraSchemaComparator();

    @Test
    void createsMissingUdtsBeforeTables() {
        UdtDefinition address = udt("address", field("street", "text"));
        TableDefinition customer = table(
                "customer",
                List.of(column("id", "uuid", true), column("address", "frozen<\"address\">", false)),
                List.of("id"),
                List.of());

        MigrationPlan plan = comparator.compare(
                "sample",
                schema(List.of(customer), List.of(address)),
                CassandraSchema.empty());

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.supportedCql()).hasSize(2);
        assertThat(plan.supportedCql().get(0))
                .isEqualTo("CREATE TYPE IF NOT EXISTS \"sample\".\"address\" (\"street\" text)");
        assertThat(plan.supportedCql().get(1))
                .contains("CREATE TABLE IF NOT EXISTS \"sample\".\"customer\"")
                .contains("PRIMARY KEY (\"id\")");
    }

    @Test
    void ordersDependentUdtsTopologically() {
        UdtDefinition geo = udt("geo", field("lat", "double"));
        UdtDefinition address = udt("address", field("geo", "frozen<\"geo\">"));

        MigrationPlan plan = comparator.compare(
                "sample",
                schema(List.of(), List.of(address, geo)),
                CassandraSchema.empty());

        assertThat(plan.supportedCql()).extracting(statement -> statement.contains("\"geo\""))
                .containsExactly(true, true);
        assertThat(plan.supportedCql().get(0)).startsWith("CREATE TYPE").contains(".\"geo\"");
        assertThat(plan.supportedCql().get(1)).startsWith("CREATE TYPE").contains(".\"address\"");
    }

    @Test
    void addsOnlyMissingNonKeyColumnsAndUdtFields() {
        UdtDefinition desiredAddress = udt(
                "address",
                field("street", "text"),
                field("postal_code", "text"));
        UdtDefinition existingAddress = udt("address", field("street", "varchar"));
        TableDefinition desiredCustomer = table(
                "customer",
                List.of(column("id", "uuid", true), column("email", "text", false)),
                List.of("id"),
                List.of());
        TableDefinition existingCustomer = table(
                "customer",
                List.of(column("id", "uuid", true)),
                List.of("id"),
                List.of());

        MigrationPlan plan = comparator.compare(
                "sample",
                schema(List.of(desiredCustomer), List.of(desiredAddress)),
                schema(List.of(existingCustomer), List.of(existingAddress)));

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.supportedCql()).containsExactly(
                "ALTER TYPE \"sample\".\"address\" ADD IF NOT EXISTS \"postal_code\" text",
                "ALTER TABLE \"sample\".\"customer\" ADD IF NOT EXISTS \"email\" text");
    }

    @Test
    void reportsDestructiveAndIncompatibleChangesWithoutGeneratingThem() {
        UdtDefinition desiredAddress = udt("address", field("street", "text"));
        UdtDefinition existingAddress = udt(
                "address",
                field("street", "int"),
                field("legacy", "text"));
        TableDefinition desiredCustomer = table(
                "customer",
                List.of(column("id", "uuid", true), column("email", "text", false)),
                List.of("id"),
                List.of());
        TableDefinition existingCustomer = table(
                "customer",
                List.of(
                        column("tenant_id", "uuid", true),
                        column("email", "int", false),
                        column("legacy", "text", false)),
                List.of("tenant_id"),
                List.of());

        MigrationPlan plan = comparator.compare(
                "sample",
                schema(List.of(desiredCustomer), List.of(desiredAddress)),
                schema(List.of(existingCustomer), List.of(existingAddress)));

        assertThat(plan.supportedCql()).isEmpty();
        assertThat(plan.unsupportedDifferences())
                .extracting(difference -> difference.type())
                .contains(
                        SchemaDifferenceType.MODIFY_PRIMARY_KEY,
                        SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                        SchemaDifferenceType.DROP_COLUMN,
                        SchemaDifferenceType.CHANGE_UDT_FIELD_TYPE,
                        SchemaDifferenceType.DROP_UDT_FIELD);
    }

    @Test
    void alwaysReportsUnmanagedObjects() {
        CassandraSchema existing = schema(
                List.of(table("legacy_table", List.of(column("id", "uuid", true)), List.of("id"), List.of())),
                List.of(udt("legacy_type", field("value", "text"))));

        MigrationPlan plan = comparator.compare("sample", CassandraSchema.empty(), existing);

        assertThat(plan.unsupportedDifferences())
                .extracting(difference -> difference.type())
                .containsExactlyInAnyOrder(SchemaDifferenceType.DROP_TABLE, SchemaDifferenceType.DROP_UDT);
    }

    @Test
    void producesEmptyPlanForMatchingSchema() {
        CassandraSchema schema = schema(
                List.of(table(
                        "customer",
                        List.of(column("id", "uuid", true), column("email", "text", false)),
                        List.of("id"),
                        List.of())),
                List.of(udt("address", field("street", "text"))));

        assertThat(comparator.compare("sample", schema, schema).isEmpty()).isTrue();
    }

    @Test
    void preservesCaseSensitiveUdtNamesDuringTypeComparison() {
        TableDefinition desired = table(
                "customer",
                List.of(
                        column("id", "uuid", true),
                        column("address", "frozen<\"Address\">", false)),
                List.of("id"),
                List.of());
        TableDefinition existing = table(
                "customer",
                List.of(
                        column("id", "uuid", true),
                        column("address", "frozen<\"address\">", false)),
                List.of("id"),
                List.of());

        MigrationPlan plan = comparator.compare(
                "sample", schema(List.of(desired), List.of()), schema(List.of(existing), List.of()));

        assertThat(plan.unsupportedDifferences())
                .extracting(difference -> difference.type())
                .containsExactly(SchemaDifferenceType.CHANGE_COLUMN_TYPE);
    }

    @Test
    void preservesInternalKeyspaceCaseWhenGeneratingCql() {
        TableDefinition customer = table(
                "customer",
                List.of(column("id", "uuid", true)),
                List.of("id"),
                List.of());

        MigrationPlan plan = comparator.compare(
                "TenantData", schema(List.of(customer), List.of()), CassandraSchema.empty());

        assertThat(plan.supportedCql().get(0))
                .startsWith("CREATE TABLE IF NOT EXISTS \"TenantData\".\"customer\"");
    }

    @Test
    void createsTablesWithClusteringOrderAndReportsExistingOrderChanges() {
        List<ColumnDefinition> columns = List.of(
                column("tenant_id", "uuid", true),
                column("occurred_at", "timestamp", true),
                column("sequence", "bigint", true));
        TableDefinition desired = table(
                "customer_event",
                columns,
                List.of("tenant_id"),
                List.of("occurred_at", "sequence"),
                Map.of(
                        "occurred_at", ClusteringOrder.DESC,
                        "sequence", ClusteringOrder.ASC));

        MigrationPlan createPlan = comparator.compare(
                "sample", schema(List.of(desired), List.of()), CassandraSchema.empty());

        assertThat(createPlan.supportedCql()).singleElement().asString()
                .endsWith("WITH CLUSTERING ORDER BY (\"occurred_at\" DESC, \"sequence\" ASC)");

        TableDefinition existing = table(
                "customer_event",
                columns,
                List.of("tenant_id"),
                List.of("occurred_at", "sequence"),
                Map.of(
                        "occurred_at", ClusteringOrder.ASC,
                        "sequence", ClusteringOrder.ASC));
        MigrationPlan incompatible = comparator.compare(
                "sample",
                schema(List.of(desired), List.of()),
                schema(List.of(existing), List.of()));

        assertThat(incompatible.supportedCql()).isEmpty();
        assertThat(incompatible.unsupportedDifferences())
                .extracting(difference -> difference.type())
                .containsExactly(SchemaDifferenceType.CHANGE_CLUSTERING_ORDER);
    }

    @Test
    void createsAndAddsStaticColumnsButNeverChangesColumnKind() {
        TableDefinition desired = table(
                "customer_event",
                List.of(
                        column("tenant_id", "uuid", true),
                        column("event_id", "uuid", true),
                        column("category", "text", false, true)),
                List.of("tenant_id"),
                List.of("event_id"));
        TableDefinition existingWithoutCategory = table(
                "customer_event",
                List.of(
                        column("tenant_id", "uuid", true),
                        column("event_id", "uuid", true)),
                List.of("tenant_id"),
                List.of("event_id"));

        MigrationPlan createPlan = comparator.compare(
                "sample", schema(List.of(desired), List.of()), CassandraSchema.empty());
        MigrationPlan alterPlan = comparator.compare(
                "sample",
                schema(List.of(desired), List.of()),
                schema(List.of(existingWithoutCategory), List.of()));

        assertThat(createPlan.supportedCql().get(0)).contains("\"category\" text STATIC");
        assertThat(alterPlan.supportedCql()).containsExactly(
                "ALTER TABLE \"sample\".\"customer_event\" ADD IF NOT EXISTS \"category\" text STATIC");

        TableDefinition existingRegularCategory = table(
                "customer_event",
                List.of(
                        column("tenant_id", "uuid", true),
                        column("event_id", "uuid", true),
                        column("category", "text", false, false)),
                List.of("tenant_id"),
                List.of("event_id"));
        MigrationPlan incompatible = comparator.compare(
                "sample",
                schema(List.of(desired), List.of()),
                schema(List.of(existingRegularCategory), List.of()));

        assertThat(incompatible.supportedCql()).isEmpty();
        assertThat(incompatible.unsupportedDifferences())
                .extracting(difference -> difference.type())
                .containsExactly(SchemaDifferenceType.CHANGE_COLUMN_KIND);
    }

    private static CassandraSchema schema(List<TableDefinition> tables, List<UdtDefinition> udts) {
        Map<String, TableDefinition> tableMap = new LinkedHashMap<>();
        tables.forEach(table -> tableMap.put(table.name(), table));
        Map<String, UdtDefinition> udtMap = new LinkedHashMap<>();
        udts.forEach(udt -> udtMap.put(udt.name(), udt));
        return new CassandraSchema(tableMap, udtMap);
    }

    private static TableDefinition table(
            String name,
            List<ColumnDefinition> columns,
            List<String> partitionKeys,
            List<String> clusteringKeys) {
        Map<String, ColumnDefinition> columnMap = new LinkedHashMap<>();
        columns.forEach(column -> columnMap.put(column.name(), column));
        return new TableDefinition(name, columnMap, partitionKeys, clusteringKeys);
    }

    private static TableDefinition table(
            String name,
            List<ColumnDefinition> columns,
            List<String> partitionKeys,
            List<String> clusteringKeys,
            Map<String, ClusteringOrder> clusteringOrders) {
        Map<String, ColumnDefinition> columnMap = new LinkedHashMap<>();
        columns.forEach(column -> columnMap.put(column.name(), column));
        return new TableDefinition(name, columnMap, partitionKeys, clusteringKeys, clusteringOrders);
    }

    private static ColumnDefinition column(String name, String type, boolean primaryKey) {
        return new ColumnDefinition(name, type, primaryKey);
    }

    private static ColumnDefinition column(String name, String type, boolean primaryKey, boolean staticColumn) {
        return new ColumnDefinition(name, type, primaryKey, staticColumn);
    }

    private static UdtDefinition udt(String name, UdtFieldDefinition... fields) {
        Map<String, UdtFieldDefinition> fieldMap = new LinkedHashMap<>();
        for (UdtFieldDefinition field : fields) {
            fieldMap.put(field.name(), field);
        }
        return new UdtDefinition(name, fieldMap);
    }

    private static UdtFieldDefinition field(String name, String type) {
        return new UdtFieldDefinition(name, type);
    }
}
