package io.github.nguyenductrongdev.automigration.cassandra.inspect;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.DataType;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlDataTypeRenderer;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads a keyspace schema from Cassandra driver metadata. */
public class CassandraSchemaInspector {

    public CassandraSchema inspect(CqlSession session, String keyspaceName) {
        CqlIdentifier keyspaceId = CqlIdentifier.fromInternal(keyspaceName);
        return session.getMetadata().getKeyspace(keyspaceId)
                .map(this::inspectKeyspace)
                .orElseThrow(() -> new IllegalStateException(
                        "Cassandra keyspace '" + keyspaceName + "' does not exist"));
    }

    private CassandraSchema inspectKeyspace(KeyspaceMetadata keyspace) {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        keyspace.getTables().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(CqlIdentifier::asInternal)))
                .forEach(entry -> {
                    TableDefinition table = inspectTable(entry.getValue());
                    tables.put(table.name(), table);
                });

        Map<String, UdtDefinition> udts = new LinkedHashMap<>();
        keyspace.getUserDefinedTypes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(CqlIdentifier::asInternal)))
                .forEach(entry -> {
                    UdtDefinition udt = inspectUdt(entry.getValue());
                    udts.put(udt.name(), udt);
                });
        return new CassandraSchema(tables, udts);
    }

    TableDefinition inspectTable(TableMetadata table) {
        List<String> partitionKeys = table.getPartitionKey().stream()
                .map(column -> column.getName().asInternal())
                .toList();
        Map<String, ClusteringOrder> clusteringOrders = new LinkedHashMap<>();
        table.getClusteringColumns().forEach((column, order) -> clusteringOrders.put(
                column.getName().asInternal(),
                order == com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC
                        ? ClusteringOrder.DESC
                        : ClusteringOrder.ASC));
        List<String> clusteringKeys = List.copyOf(clusteringOrders.keySet());
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();
        table.getColumns().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(CqlIdentifier::asInternal)))
                .forEach(entry -> {
                    ColumnMetadata column = entry.getValue();
                    String name = column.getName().asInternal();
                    boolean primaryKey = partitionKeys.contains(name) || clusteringKeys.contains(name);
                    columns.put(name, new ColumnDefinition(
                            name, cqlType(column.getType()), primaryKey, column.isStatic()));
                });
        return new TableDefinition(
                table.getName().asInternal(),
                columns,
                partitionKeys,
                clusteringKeys,
                clusteringOrders);
    }

    private UdtDefinition inspectUdt(UserDefinedType udt) {
        Map<String, UdtFieldDefinition> fields = new LinkedHashMap<>();
        List<CqlIdentifier> fieldNames = udt.getFieldNames();
        for (int index = 0; index < fieldNames.size(); index++) {
            String name = fieldNames.get(index).asInternal();
            fields.put(name, new UdtFieldDefinition(name, cqlType(udt.getFieldTypes().get(index))));
        }
        return new UdtDefinition(udt.getName().asInternal(), fields);
    }

    String cqlType(DataType type) {
        return CqlDataTypeRenderer.render(type);
    }

}
