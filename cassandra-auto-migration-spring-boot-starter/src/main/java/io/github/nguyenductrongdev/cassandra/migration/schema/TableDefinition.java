package io.github.nguyenductrongdev.cassandra.migration.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Desired or existing Cassandra table shape. */
public record TableDefinition(
        String name,
        Map<String, ColumnDefinition> columns,
        List<String> partitionKeyColumns,
        List<String> clusteringKeyColumns,
        Map<String, ClusteringOrder> clusteringOrders) {

    public TableDefinition {
        columns = Map.copyOf(columns);
        partitionKeyColumns = List.copyOf(partitionKeyColumns);
        clusteringKeyColumns = List.copyOf(clusteringKeyColumns);

        Objects.requireNonNull(clusteringOrders, "clusteringOrders");
        if (!clusteringKeyColumns.containsAll(clusteringOrders.keySet())) {
            throw new IllegalArgumentException("Clustering orders must reference clustering-key columns only");
        }
        Map<String, ClusteringOrder> normalizedOrders = new LinkedHashMap<>();
        for (String column : clusteringKeyColumns) {
            normalizedOrders.put(column, Objects.requireNonNull(
                    clusteringOrders.getOrDefault(column, ClusteringOrder.ASC),
                    "Clustering order for " + column));
        }
        clusteringOrders = Collections.unmodifiableMap(normalizedOrders);
    }

    public TableDefinition(
            String name,
            Map<String, ColumnDefinition> columns,
            List<String> partitionKeyColumns,
            List<String> clusteringKeyColumns) {
        this(name, columns, partitionKeyColumns, clusteringKeyColumns, Map.of());
    }
}
