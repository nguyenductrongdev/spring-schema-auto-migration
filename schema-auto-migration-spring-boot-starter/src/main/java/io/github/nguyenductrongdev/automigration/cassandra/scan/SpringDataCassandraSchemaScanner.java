package io.github.nguyenductrongdev.automigration.cassandra.scan;

import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlNames;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentProperty;
import org.springframework.data.cassandra.core.mapping.Frozen;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.VectorType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the desired schema from Spring Data Cassandra mapping metadata. */
public class SpringDataCassandraSchemaScanner {

    private final JavaTypeResolver typeResolver;

    public SpringDataCassandraSchemaScanner(JavaTypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    public CassandraSchema scan(CassandraMappingContext mappingContext) {
        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        Map<String, UdtDefinition> udts = new LinkedHashMap<>();

        mappingContext.getPersistentEntities().stream()
                .sorted(Comparator.comparing(entity -> entity.getType().getName()))
                .forEach(entity -> {
                    if (entity.isUserDefinedType()) {
                        UdtDefinition udt = scanUdt(entity, mappingContext);
                        udts.put(udt.name(), udt);
                    }
                    if (entity.getType().isAnnotationPresent(Table.class)) {
                        TableDefinition table = scanTable(entity, mappingContext);
                        tables.put(table.name(), table);
                    }
                });

        return new CassandraSchema(tables, udts);
    }

    private TableDefinition scanTable(
            CassandraPersistentEntity<?> entity,
            CassandraMappingContext mappingContext) {
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();
        List<KeyPart> partitionKeys = new ArrayList<>();
        List<KeyPart> clusteringKeys = new ArrayList<>();

        for (CassandraPersistentProperty property : entity) {
            if (property.isTransient()) {
                continue;
            }
            if (property.isCompositePrimaryKey()) {
                CassandraPersistentEntity<?> primaryKeyEntity =
                        mappingContext.getRequiredPersistentEntity(property.getType());
                scanPrimaryKeyEntity(primaryKeyEntity, mappingContext, columns, partitionKeys, clusteringKeys);
            } else {
                scanProperty(property, mappingContext, columns, partitionKeys, clusteringKeys);
            }
        }

        if (partitionKeys.isEmpty()) {
            throw new IllegalStateException("Mapped table " + entity.getType().getName() + " has no partition key");
        }
        return new TableDefinition(
                entity.getTableName().asInternal(),
                columns,
                keyNames(partitionKeys),
                keyNames(clusteringKeys),
                clusteringOrders(clusteringKeys));
    }

    private void scanPrimaryKeyEntity(
            CassandraPersistentEntity<?> keyEntity,
            CassandraMappingContext mappingContext,
            Map<String, ColumnDefinition> columns,
            List<KeyPart> partitionKeys,
            List<KeyPart> clusteringKeys) {
        for (CassandraPersistentProperty property : keyEntity) {
            if (!property.isPrimaryKeyColumn()) {
                throw new IllegalStateException("Every property in primary key class "
                        + keyEntity.getType().getName() + " must use @PrimaryKeyColumn");
            }
            scanProperty(property, mappingContext, columns, partitionKeys, clusteringKeys);
        }
    }

    private void scanProperty(
            CassandraPersistentProperty property,
            CassandraMappingContext mappingContext,
            Map<String, ColumnDefinition> columns,
            List<KeyPart> partitionKeys,
            List<KeyPart> clusteringKeys) {
        String columnName = property.getColumnName().asInternal();
        boolean primaryKey = property.isIdProperty() || property.isPrimaryKeyColumn();
        columns.put(columnName, new ColumnDefinition(
                columnName,
                cqlType(property, mappingContext),
                primaryKey,
                property.isStaticColumn()));

        int ordinal = property.hasOrdinal() ? property.getRequiredOrdinal() : 0;
        if (property.isPartitionKeyColumn()
                || (property.isIdProperty() && !property.isClusterKeyColumn())) {
            partitionKeys.add(new KeyPart(ordinal, columnName, ClusteringOrder.ASC));
        } else if (property.isClusterKeyColumn()) {
            clusteringKeys.add(new KeyPart(
                    ordinal,
                    columnName,
                    clusteringOrder(property.getPrimaryKeyOrdering())));
        }
    }

    private UdtDefinition scanUdt(
            CassandraPersistentEntity<?> entity, CassandraMappingContext mappingContext) {
        Map<String, UdtFieldDefinition> fields = new LinkedHashMap<>();
        for (CassandraPersistentProperty property : entity) {
            if (!property.isTransient()) {
                String fieldName = property.getColumnName().asInternal();
                fields.put(fieldName, new UdtFieldDefinition(fieldName, cqlType(property, mappingContext)));
            }
        }
        return new UdtDefinition(entity.getTableName().asInternal(), fields);
    }

    private String cqlType(
            CassandraPersistentProperty property,
            CassandraMappingContext mappingContext) {
        String resolved;
        VectorType vector = property.findAnnotation(VectorType.class);
        if (vector != null) {
            resolved = "vector<" + scalarAnnotationType(vector.subtype())
                    + ", " + vector.dimensions() + ">";
        } else {
            CassandraType explicitType = property.findAnnotation(CassandraType.class);
            resolved = explicitType == null
                    ? typeResolver.resolve(
                            genericType(property), type -> mappedUdtName(mappingContext, type))
                    : explicitAnnotationType(explicitType);
        }

        if (property.isAnnotationPresent(Frozen.class) && !resolved.startsWith("frozen<")) {
            return "frozen<" + resolved + ">";
        }
        return resolved;
    }

    private String explicitAnnotationType(CassandraType annotation) {
        CassandraType.Name[] arguments = annotation.typeArguments();
        return switch (annotation.type()) {
            case LIST -> collectionAnnotationType("list", arguments, 1, annotation.userTypeName());
            case SET -> collectionAnnotationType("set", arguments, 1, annotation.userTypeName());
            case MAP -> collectionAnnotationType("map", arguments, 2, annotation.userTypeName());
            case TUPLE -> tupleAnnotationType(arguments, annotation.userTypeName());
            case UDT -> udtAnnotationType(annotation.userTypeName(), false);
            case VECTOR -> throw new IllegalArgumentException(
                    "Use @VectorType to declare a Cassandra vector property");
            default -> scalarAnnotationType(annotation.type());
        };
    }

    private String collectionAnnotationType(
            String collection,
            CassandraType.Name[] arguments,
            int expectedArguments,
            String userTypeName) {
        if (arguments.length != expectedArguments) {
            throw new IllegalArgumentException("@CassandraType " + collection
                    + " requires " + expectedArguments + " type argument(s)");
        }
        List<String> types = new ArrayList<>(arguments.length);
        for (CassandraType.Name argument : arguments) {
            types.add(annotationArgumentType(argument, userTypeName));
        }
        return collection + "<" + String.join(", ", types) + ">";
    }

    private String tupleAnnotationType(CassandraType.Name[] arguments, String userTypeName) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("@CassandraType tuple requires at least one type argument");
        }
        List<String> types = new ArrayList<>(arguments.length);
        for (CassandraType.Name argument : arguments) {
            types.add(annotationArgumentType(argument, userTypeName));
        }
        return "tuple<" + String.join(", ", types) + ">";
    }

    private String annotationArgumentType(CassandraType.Name type, String userTypeName) {
        return type == CassandraType.Name.UDT
                ? udtAnnotationType(userTypeName, true)
                : scalarAnnotationType(type);
    }

    private String udtAnnotationType(String userTypeName, boolean frozen) {
        if (userTypeName == null || userTypeName.isBlank()) {
            throw new IllegalArgumentException("@CassandraType UDT requires userTypeName");
        }
        String name = CqlNames.quote(CqlNames.internal(userTypeName));
        return frozen ? "frozen<" + name + ">" : name;
    }

    private String scalarAnnotationType(CassandraType.Name type) {
        return switch (type) {
            case LIST, SET, MAP, TUPLE, UDT, VECTOR ->
                    throw new IllegalArgumentException("Cassandra type " + type + " is not scalar");
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    private String mappedUdtName(CassandraMappingContext mappingContext, Class<?> type) {
        CassandraPersistentEntity<?> entity = mappingContext.getPersistentEntity(type);
        if (entity != null && entity.isUserDefinedType()) {
            return entity.getTableName().asInternal();
        }
        return null;
    }

    private Type genericType(CassandraPersistentProperty property) {
        Field field = property.getField();
        if (field != null) {
            return field.getGenericType();
        }
        Method getter = property.getGetter();
        if (getter != null) {
            return getter.getGenericReturnType();
        }
        return property.getType();
    }

    private List<String> keyNames(List<KeyPart> parts) {
        return parts.stream()
                .sorted(Comparator.comparingInt(KeyPart::ordinal))
                .map(KeyPart::name)
                .toList();
    }

    private Map<String, ClusteringOrder> clusteringOrders(List<KeyPart> parts) {
        Map<String, ClusteringOrder> orders = new LinkedHashMap<>();
        parts.stream()
                .sorted(Comparator.comparingInt(KeyPart::ordinal))
                .forEach(part -> orders.put(part.name(), part.clusteringOrder()));
        return orders;
    }

    private ClusteringOrder clusteringOrder(Ordering ordering) {
        return ordering == Ordering.DESCENDING ? ClusteringOrder.DESC : ClusteringOrder.ASC;
    }

    private record KeyPart(int ordinal, String name, ClusteringOrder clusteringOrder) {
    }
}
