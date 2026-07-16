package io.github.nguyenductrongdev.automigration.cassandra.scan;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataType;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlDataTypeRenderer;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlNames;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.SchemaFactory;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.keyspace.ColumnSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.CreateTableSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.CreateUserTypeSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.FieldSpecification;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds the desired schema using Spring Data Cassandra's public schema mapping API. */
public class SpringDataCassandraSchemaScanner {

    private final CassandraMappingContext mappingContext;
    private final SchemaFactory schemaFactory;

    public SpringDataCassandraSchemaScanner(CassandraConverter converter) {
        this.mappingContext = converter.getMappingContext();
        this.schemaFactory = new SchemaFactory(converter);
    }

    public CassandraSchema scan() {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        Map<String, UdtDefinition> udts = new LinkedHashMap<>();
        Set<String> mappedUdtNames = mappingContext.getPersistentEntities().stream()
                .filter(CassandraPersistentEntity::isUserDefinedType)
                .map(entity -> entity.getTableName().asInternal())
                .collect(Collectors.toSet());

        mappingContext.getPersistentEntities().stream()
                .sorted(Comparator.comparing(entity -> entity.getType().getName()))
                .forEach(entity -> {
                    if (entity.isUserDefinedType()) {
                        UdtDefinition udt = scanUdt(entity, mappedUdtNames);
                        udts.put(udt.name(), udt);
                    }
                    if (entity.getType().isAnnotationPresent(Table.class)) {
                        TableDefinition table = scanTable(entity);
                        tables.put(table.name(), table);
                    }
                });

        return new CassandraSchema(tables, udts);
    }

    private TableDefinition scanTable(CassandraPersistentEntity<?> entity) {
        CreateTableSpecification specification = schemaFactory.getCreateTableSpecificationFor(entity);
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();

        for (ColumnSpecification column : specification.getColumns()) {
            String name = column.getName().asInternal();
            DataType type = Objects.requireNonNull(
                    column.getType(), "Cassandra type for column " + name);
            columns.put(name, new ColumnDefinition(
                    name,
                    CqlDataTypeRenderer.render(type),
                    column.getKeyType() != null,
                    column.isStatic()));
        }

        List<String> partitionKeys = columnNames(specification.getPartitionKeyColumns());
        List<String> clusteringKeys = columnNames(specification.getClusteredKeyColumns());
        Map<String, ClusteringOrder> clusteringOrders = new LinkedHashMap<>();
        for (ColumnSpecification column : specification.getClusteredKeyColumns()) {
            clusteringOrders.put(
                    column.getName().asInternal(),
                    column.getOrdering() == Ordering.DESCENDING
                            ? ClusteringOrder.DESC
                            : ClusteringOrder.ASC);
        }

        return new TableDefinition(
                entity.getTableName().asInternal(),
                columns,
                partitionKeys,
                clusteringKeys,
                clusteringOrders);
    }

    private UdtDefinition scanUdt(CassandraPersistentEntity<?> entity, Set<String> mappedUdtNames) {
        CreateUserTypeSpecification specification = schemaFactory.getCreateUserTypeSpecificationFor(entity);
        Map<String, UdtFieldDefinition> fields = new LinkedHashMap<>();
        for (FieldSpecification field : specification.getFields()) {
            RenderedField rendered = parseRenderedField(field.toCql());
            fields.put(rendered.name(), new UdtFieldDefinition(
                    rendered.name(), quoteMappedUdtNames(rendered.cqlType(), mappedUdtNames)));
        }
        return new UdtDefinition(entity.getTableName().asInternal(), fields);
    }

    private List<String> columnNames(List<ColumnSpecification> columns) {
        return columns.stream().map(column -> column.getName().asInternal()).toList();
    }

    private RenderedField parseRenderedField(String cql) {
        int identifierEnd = identifierEnd(cql);
        int typeStart = identifierEnd;
        while (typeStart < cql.length() && Character.isWhitespace(cql.charAt(typeStart))) {
            typeStart++;
        }
        if (typeStart == identifierEnd || typeStart == cql.length()) {
            throw new IllegalArgumentException("Invalid Spring Data Cassandra field specification: " + cql);
        }

        String identifier = cql.substring(0, identifierEnd);
        String name = CqlIdentifier.fromCql(identifier).asInternal();
        return new RenderedField(name, cql.substring(typeStart));
    }

    private int identifierEnd(String cql) {
        if (cql.isEmpty()) {
            throw new IllegalArgumentException("Spring Data Cassandra rendered an empty field specification");
        }
        if (cql.charAt(0) != '"') {
            for (int index = 0; index < cql.length(); index++) {
                if (Character.isWhitespace(cql.charAt(index))) {
                    return index;
                }
            }
            return cql.length();
        }

        for (int index = 1; index < cql.length(); index++) {
            if (cql.charAt(index) != '"') {
                continue;
            }
            if (index + 1 < cql.length() && cql.charAt(index + 1) == '"') {
                index++;
                continue;
            }
            return index + 1;
        }
        throw new IllegalArgumentException("Unterminated CQL identifier in field specification: " + cql);
    }

    private String quoteMappedUdtNames(String cqlType, Set<String> mappedUdtNames) {
        StringBuilder result = new StringBuilder(cqlType.length());
        for (int index = 0; index < cqlType.length();) {
            char character = cqlType.charAt(index);
            if (character == '"') {
                int end = quotedIdentifierEnd(cqlType, index);
                result.append(cqlType, index, end);
                index = end;
                continue;
            }
            if (Character.isLetter(character) || character == '_') {
                int end = index + 1;
                while (end < cqlType.length()) {
                    char tokenCharacter = cqlType.charAt(end);
                    if (!Character.isLetterOrDigit(tokenCharacter) && tokenCharacter != '_') {
                        break;
                    }
                    end++;
                }
                String token = cqlType.substring(index, end);
                result.append(mappedUdtNames.contains(token) ? CqlNames.quote(token) : token);
                index = end;
                continue;
            }
            result.append(character);
            index++;
        }
        return result.toString();
    }

    private int quotedIdentifierEnd(String cql, int start) {
        for (int index = start + 1; index < cql.length(); index++) {
            if (cql.charAt(index) != '"') {
                continue;
            }
            if (index + 1 < cql.length() && cql.charAt(index + 1) == '"') {
                index++;
                continue;
            }
            return index + 1;
        }
        throw new IllegalArgumentException("Unterminated CQL identifier in type: " + cql);
    }

    private record RenderedField(String name, String cqlType) {
    }
}
