package io.github.nguyenductrongdev.automigration.cassandra.inspect;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.type.DefaultUserDefinedType;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CassandraSchemaInspectorTest {

    private final CassandraSchemaInspector inspector = new CassandraSchemaInspector();

    @Test
    void removesUdtKeyspaceQualifierWhilePreservingFrozenAndIdentifierCase() {
        var address = new DefaultUserDefinedType(
                CqlIdentifier.fromInternal("TenantData"),
                CqlIdentifier.fromInternal("Address"),
                true,
                List.of(CqlIdentifier.fromInternal("street")),
                List.of(DataTypes.TEXT));

        assertThat(inspector.cqlType(address)).isEqualTo("frozen<\"Address\">");
        assertThat(inspector.cqlType(DataTypes.listOf(address)))
                .isEqualTo("list<frozen<\"Address\">>");
        assertThat(inspector.cqlType(DataTypes.frozenMapOf(DataTypes.TEXT, address)))
                .isEqualTo("frozen<map<text, frozen<\"Address\">>>");
    }

    @Test
    void preservesClusteringOrderFromDriverMetadata() {
        ColumnMetadata tenantId = column("tenant_id", DataTypes.UUID);
        ColumnMetadata occurredAt = column("occurred_at", DataTypes.TIMESTAMP);
        ColumnMetadata sequence = column("sequence", DataTypes.BIGINT);

        Map<ColumnMetadata, com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder>
                driverClusteringOrders = new LinkedHashMap<>();
        driverClusteringOrders.put(
                occurredAt, com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC);
        driverClusteringOrders.put(
                sequence, com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.ASC);

        Map<CqlIdentifier, ColumnMetadata> columns = new LinkedHashMap<>();
        columns.put(tenantId.getName(), tenantId);
        columns.put(occurredAt.getName(), occurredAt);
        columns.put(sequence.getName(), sequence);

        TableMetadata metadata = mock(TableMetadata.class);
        when(metadata.getName()).thenReturn(CqlIdentifier.fromInternal("customer_event"));
        when(metadata.getPartitionKey()).thenReturn(List.of(tenantId));
        when(metadata.getClusteringColumns()).thenReturn(driverClusteringOrders);
        when(metadata.getColumns()).thenReturn(columns);

        TableDefinition table = inspector.inspectTable(metadata);

        assertThat(table.partitionKeyColumns()).containsExactly("tenant_id");
        assertThat(table.clusteringKeyColumns()).containsExactly("occurred_at", "sequence");
        assertThat(table.clusteringOrders()).containsExactly(
                Map.entry("occurred_at", ClusteringOrder.DESC),
                Map.entry("sequence", ClusteringOrder.ASC));
    }

    private ColumnMetadata column(String name, DataType type) {
        ColumnMetadata column = mock(ColumnMetadata.class);
        when(column.getName()).thenReturn(CqlIdentifier.fromInternal(name));
        when(column.getType()).thenReturn(type);
        return column;
    }
}
