package io.github.nguyenductrongdev.automigration.cassandra.compare;

import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlNames;
import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifference;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifferenceType;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Produces an additive-only migration plan by comparing desired and existing schemas. */
public class CassandraSchemaComparator {

    public MigrationPlan compare(
            String keyspace,
            CassandraSchema desired,
            CassandraSchema existing) {
        List<String> supported = new ArrayList<>();
        List<SchemaDifference> unsupported = new ArrayList<>();

        compareUdts(keyspace, desired, existing, supported, unsupported);
        compareTables(keyspace, desired, existing, supported, unsupported);
        reportUnmanaged(desired, existing, unsupported);
        return new MigrationPlan(supported, unsupported);
    }

    private void compareUdts(
            String keyspace,
            CassandraSchema desired,
            CassandraSchema existing,
            List<String> supported,
            List<SchemaDifference> unsupported) {
        List<UdtDefinition> missingUdts = desired.udts().values().stream()
                .filter(udt -> !existing.udts().containsKey(udt.name()))
                .toList();
        for (UdtDefinition udt : orderMissingUdts(missingUdts)) {
            supported.add(createUdtCql(keyspace, udt));
        }

        desired.udts().values().stream()
                .sorted(Comparator.comparing(UdtDefinition::name))
                .filter(udt -> existing.udts().containsKey(udt.name()))
                .forEach(desiredUdt -> compareExistingUdt(
                        keyspace, desiredUdt, existing.udts().get(desiredUdt.name()), supported, unsupported));
    }

    private void compareExistingUdt(
            String keyspace,
            UdtDefinition desired,
            UdtDefinition existing,
            List<String> supported,
            List<SchemaDifference> unsupported) {
        desired.fields().values().stream()
                .sorted(Comparator.comparing(UdtFieldDefinition::name))
                .forEach(field -> {
                    UdtFieldDefinition existingField = existing.fields().get(field.name());
                    if (existingField == null) {
                        supported.add("ALTER TYPE " + CqlNames.qualified(keyspace, desired.name())
                                + " ADD IF NOT EXISTS " + CqlNames.quote(field.name()) + " " + field.cqlType());
                    } else if (!sameType(field.cqlType(), existingField.cqlType())) {
                        unsupported.add(new SchemaDifference(
                                SchemaDifferenceType.CHANGE_UDT_FIELD_TYPE,
                                desired.name() + "." + field.name(),
                                "UDT field type differs: expected " + field.cqlType()
                                        + ", found " + existingField.cqlType()));
                    }
                });

        existing.fields().keySet().stream()
                .filter(name -> !desired.fields().containsKey(name))
                .sorted()
                .forEach(name -> unsupported.add(new SchemaDifference(
                        SchemaDifferenceType.DROP_UDT_FIELD,
                        desired.name() + "." + name,
                        "Existing UDT field is not mapped and would require DROP")));
    }

    private void compareTables(
            String keyspace,
            CassandraSchema desired,
            CassandraSchema existing,
            List<String> supported,
            List<SchemaDifference> unsupported) {
        desired.tables().values().stream()
                .sorted(Comparator.comparing(TableDefinition::name))
                .forEach(desiredTable -> {
                    TableDefinition existingTable = existing.tables().get(desiredTable.name());
                    if (existingTable == null) {
                        supported.add(createTableCql(keyspace, desiredTable));
                    } else {
                        compareExistingTable(keyspace, desiredTable, existingTable, supported, unsupported);
                    }
                });
    }

    private void compareExistingTable(
            String keyspace,
            TableDefinition desired,
            TableDefinition existing,
            List<String> supported,
            List<SchemaDifference> unsupported) {
        boolean primaryKeyDiffers = !desired.partitionKeyColumns().equals(existing.partitionKeyColumns())
                || !desired.clusteringKeyColumns().equals(existing.clusteringKeyColumns());
        if (primaryKeyDiffers) {
            unsupported.add(new SchemaDifference(
                    SchemaDifferenceType.MODIFY_PRIMARY_KEY,
                    desired.name(),
                    "Primary key differs: expected " + primaryKeyDescription(desired)
                            + ", found " + primaryKeyDescription(existing)));
        } else if (!desired.clusteringOrders().equals(existing.clusteringOrders())) {
            unsupported.add(new SchemaDifference(
                    SchemaDifferenceType.CHANGE_CLUSTERING_ORDER,
                    desired.name(),
                    "Clustering order differs: expected " + clusteringOrderDescription(desired)
                            + ", found " + clusteringOrderDescription(existing)));
        }

        desired.columns().values().stream()
                .sorted(Comparator.comparing(ColumnDefinition::name))
                .forEach(column -> {
                    ColumnDefinition existingColumn = existing.columns().get(column.name());
                    if (existingColumn == null) {
                        if (column.primaryKey()) {
                            unsupported.add(new SchemaDifference(
                                    SchemaDifferenceType.MODIFY_PRIMARY_KEY,
                                    desired.name() + "." + column.name(),
                                    "Missing primary-key column cannot be added to an existing table"));
                        } else {
                            supported.add("ALTER TABLE " + CqlNames.qualified(keyspace, desired.name())
                                    + " ADD IF NOT EXISTS " + CqlNames.quote(column.name()) + " "
                                    + column.cqlType() + (column.staticColumn() ? " STATIC" : ""));
                        }
                    } else {
                        if (!sameType(column.cqlType(), existingColumn.cqlType())) {
                            unsupported.add(new SchemaDifference(
                                    SchemaDifferenceType.CHANGE_COLUMN_TYPE,
                                    desired.name() + "." + column.name(),
                                    "Column type differs: expected " + column.cqlType()
                                            + ", found " + existingColumn.cqlType()));
                        }
                        if (column.staticColumn() != existingColumn.staticColumn()) {
                            unsupported.add(new SchemaDifference(
                                    SchemaDifferenceType.CHANGE_COLUMN_KIND,
                                    desired.name() + "." + column.name(),
                                    "Column kind differs: expected "
                                            + (column.staticColumn() ? "STATIC" : "REGULAR")
                                            + ", found "
                                            + (existingColumn.staticColumn() ? "STATIC" : "REGULAR")));
                        }
                    }
                });

        existing.columns().keySet().stream()
                .filter(name -> !desired.columns().containsKey(name))
                .sorted()
                .forEach(name -> unsupported.add(new SchemaDifference(
                        SchemaDifferenceType.DROP_COLUMN,
                        desired.name() + "." + name,
                        "Existing column is not mapped and would require DROP")));
    }

    private void reportUnmanaged(
            CassandraSchema desired,
            CassandraSchema existing,
            List<SchemaDifference> unsupported) {
        existing.tables().keySet().stream()
                .filter(name -> !desired.tables().containsKey(name))
                .sorted()
                .forEach(name -> unsupported.add(new SchemaDifference(
                        SchemaDifferenceType.DROP_TABLE,
                        name,
                        "Existing table is not managed by a mapped entity")));
        existing.udts().keySet().stream()
                .filter(name -> !desired.udts().containsKey(name))
                .sorted()
                .forEach(name -> unsupported.add(new SchemaDifference(
                        SchemaDifferenceType.DROP_UDT,
                        name,
                        "Existing UDT is not managed by a mapped type")));
    }

    private String createUdtCql(String keyspace, UdtDefinition udt) {
        String fields = udt.fields().values().stream()
                .sorted(Comparator.comparing(UdtFieldDefinition::name))
                .map(field -> CqlNames.quote(field.name()) + " " + field.cqlType())
                .collect(Collectors.joining(", "));
        return "CREATE TYPE IF NOT EXISTS " + CqlNames.qualified(keyspace, udt.name()) + " (" + fields + ")";
    }

    private String createTableCql(String keyspace, TableDefinition table) {
        if (table.partitionKeyColumns().isEmpty()) {
            throw new IllegalArgumentException("Cannot create table " + table.name() + " without a partition key");
        }
        List<String> definitions = table.columns().values().stream()
                .sorted(Comparator.comparing(ColumnDefinition::name))
                .map(column -> CqlNames.quote(column.name()) + " " + column.cqlType()
                        + (column.staticColumn() ? " STATIC" : ""))
                .collect(Collectors.toCollection(ArrayList::new));
        definitions.add(primaryKeyCql(table));
        return "CREATE TABLE IF NOT EXISTS " + CqlNames.qualified(keyspace, table.name())
                + " (" + String.join(", ", definitions) + ")"
                + clusteringOrderCql(table);
    }

    private String primaryKeyCql(TableDefinition table) {
        String partition = table.partitionKeyColumns().stream()
                .map(CqlNames::quote)
                .collect(Collectors.joining(", "));
        if (table.partitionKeyColumns().size() > 1) {
            partition = "(" + partition + ")";
        }
        String clustering = table.clusteringKeyColumns().stream()
                .map(CqlNames::quote)
                .collect(Collectors.joining(", "));
        return "PRIMARY KEY (" + partition + (clustering.isEmpty() ? "" : ", " + clustering) + ")";
    }

    private String primaryKeyDescription(TableDefinition table) {
        return "partition=" + table.partitionKeyColumns() + ", clustering=" + table.clusteringKeyColumns();
    }

    private String clusteringOrderCql(TableDefinition table) {
        if (table.clusteringKeyColumns().isEmpty()) {
            return "";
        }
        String order = table.clusteringKeyColumns().stream()
                .map(column -> CqlNames.quote(column) + " " + table.clusteringOrders().get(column).name())
                .collect(Collectors.joining(", "));
        return " WITH CLUSTERING ORDER BY (" + order + ")";
    }

    private String clusteringOrderDescription(TableDefinition table) {
        return table.clusteringKeyColumns().stream()
                .map(column -> column + " " + table.clusteringOrders().get(column).name())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private boolean sameType(String desired, String existing) {
        return canonicalType(desired).equals(canonicalType(existing));
    }

    private String canonicalType(String type) {
        StringBuilder canonical = new StringBuilder(type.length());
        StringBuilder token = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < type.length(); index++) {
            char character = type.charAt(index);
            if (quoted) {
                canonical.append(character);
                if (character == '\"') {
                    if (index + 1 < type.length() && type.charAt(index + 1) == '\"') {
                        canonical.append(type.charAt(++index));
                    } else {
                        quoted = false;
                    }
                }
            } else if (character == '\"') {
                appendTypeToken(canonical, token);
                canonical.append(character);
                quoted = true;
            } else if (Character.isWhitespace(character)) {
                appendTypeToken(canonical, token);
            } else if (Character.isLetterOrDigit(character) || character == '_') {
                token.append(character);
            } else {
                appendTypeToken(canonical, token);
                canonical.append(character);
            }
        }
        appendTypeToken(canonical, token);
        return canonical.toString();
    }

    private void appendTypeToken(StringBuilder target, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String normalized = token.toString().toLowerCase(Locale.ROOT);
        target.append(normalized.equals("varchar") ? "text" : normalized);
        token.setLength(0);
    }

    private List<UdtDefinition> orderMissingUdts(List<UdtDefinition> missing) {
        List<UdtDefinition> remaining = new ArrayList<>(missing);
        remaining.sort(Comparator.comparing(UdtDefinition::name));
        Set<String> pendingNames = remaining.stream().map(UdtDefinition::name).collect(Collectors.toSet());
        Set<String> emitted = new HashSet<>();
        List<UdtDefinition> result = new ArrayList<>();

        while (!remaining.isEmpty()) {
            UdtDefinition next = remaining.stream()
                    .filter(candidate -> dependencies(candidate, pendingNames).stream().allMatch(emitted::contains))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cyclic dependency detected between mapped Cassandra UDTs"));
            result.add(next);
            emitted.add(next.name());
            remaining.remove(next);
        }
        return result;
    }

    private Set<String> dependencies(UdtDefinition udt, Set<String> candidateNames) {
        Set<String> dependencies = new HashSet<>();
        for (String candidate : candidateNames) {
            if (!candidate.equals(udt.name()) && udt.fields().values().stream()
                    .map(UdtFieldDefinition::cqlType)
                    .map(this::canonicalType)
                    .anyMatch(type -> type.contains(canonicalType(
                            "frozen<" + CqlNames.quote(candidate) + ">")))) {
                dependencies.add(candidate);
            }
        }
        return dependencies;
    }
}
