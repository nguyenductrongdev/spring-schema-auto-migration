package io.github.nguyenductrongdev.automigration.cassandra.scan;

import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.cassandra.core.convert.CassandraCustomConversions;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Frozen;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;
import org.springframework.data.convert.WritingConverter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDataCassandraSchemaScannerTest {

    @Test
    void scansTablesReferencedUdtsAndExplicitColumnNames() {
        CassandraSchema schema = scan(Customer.class, Address.class, Coordinates.class);

        assertThat(schema.tables()).containsOnlyKeys("customers");
        assertThat(schema.udts()).containsOnlyKeys("address", "coordinates");
        assertThat(schema.tables().get("customers").columns())
                .containsOnlyKeys("id", "email_address", "address");
        assertThat(schema.tables().get("customers").partitionKeyColumns()).containsExactly("id");
        assertThat(schema.udts().get("address").fields())
                .containsOnlyKeys("street", "postal_code", "display name", "coordinates");
        assertThat(schema.udts().get("address").fields().get("coordinates").cqlType())
                .isEqualTo("frozen<\"coordinates\">");
    }

    @Test
    void expandsCompositePrimaryKeyClassesInOrdinalOrder() {
        CassandraSchema schema = scan(CustomerEvent.class, EventKey.class);

        TableDefinition table = schema.tables().get("customerevent");
        assertThat(table.partitionKeyColumns()).containsExactly("tenant_id");
        assertThat(table.clusteringKeyColumns()).containsExactly("occurred_at");
        assertThat(table.clusteringOrders()).containsEntry("occurred_at", ClusteringOrder.DESC);
        assertThat(table.columns()).containsKeys("tenant_id", "occurred_at", "payload", "category");
        assertThat(table.columns().get("category").staticColumn()).isTrue();
        assertThat(table.columns()).doesNotContainKey("key");
    }

    @Test
    void usesSpringMappingsForDefaultNamesTypesAndAnnotations() {
        CassandraSchema schema = scan(DefaultNamedCustomer.class);

        assertThat(schema.tables()).containsOnlyKeys("defaultnamedcustomer");
        assertThat(schema.udts()).containsOnlyKeys("postaladdress");
        assertThat(schema.tables().get("defaultnamedcustomer").columns())
                .containsOnlyKeys("id", "postaladdress", "version", "tags", "scores", "createdat");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("postaladdress").cqlType())
                .isEqualTo("frozen<\"postaladdress\">");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("version").cqlType())
                .isEqualTo("timeuuid");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("tags").cqlType())
                .isEqualTo("frozen<list<text>>");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("scores").cqlType())
                .isEqualTo("map<text, int>");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("createdat").cqlType())
                .isEqualTo("timestamp");
        assertThat(schema.udts().get("postaladdress").fields()).containsOnlyKeys("postalcode");
    }

    @Test
    void honorsApplicationCustomWritingConverters() {
        CassandraSchema schema = scan(
                List.of(ExternalReferenceWriteConverter.INSTANCE),
                ConvertedEntity.class);

        assertThat(schema.tables().get("convertedentity").columns().get("reference").cqlType())
                .isEqualTo("text");
    }

    private CassandraSchema scan(Class<?>... types) {
        return scan(List.of(), types);
    }

    @SuppressWarnings("removal")
    private CassandraSchema scan(List<?> converters, Class<?>... types) {
        CassandraCustomConversions customConversions = new CassandraCustomConversions(converters);
        CassandraMappingContext context = new CassandraMappingContext();
        context.setSimpleTypeHolder(customConversions.getSimpleTypeHolder());
        context.setInitialEntitySet(Set.of(types));
        context.afterPropertiesSet();

        MappingCassandraConverter converter = new MappingCassandraConverter(context);
        converter.setCustomConversions(customConversions);
        converter.afterPropertiesSet();
        return new SpringDataCassandraSchemaScanner(converter).scan();
    }

    @Table("customers")
    private static class Customer {
        @PrimaryKey
        private UUID id;

        @Column("email_address")
        private String email;

        private Address address;
    }

    @UserDefinedType("address")
    private static class Address {
        private String street;

        @Column("postal_code")
        private String postalCode;

        @Column("display name")
        private String displayName;

        private Coordinates coordinates;
    }

    @UserDefinedType("coordinates")
    private static class Coordinates {
        private double latitude;
        private double longitude;
    }

    @Table
    private static class CustomerEvent {
        @PrimaryKey
        private EventKey key;

        private String payload;

        @Column(isStatic = true)
        private String category;
    }

    @PrimaryKeyClass
    private static class EventKey {
        @PrimaryKeyColumn(name = "tenant_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
        private UUID tenantId;

        @PrimaryKeyColumn(
                name = "occurred_at",
                ordinal = 0,
                type = PrimaryKeyType.CLUSTERED,
                ordering = Ordering.DESCENDING)
        private Instant occurredAt;
    }

    @Table
    private static class DefaultNamedCustomer {
        @PrimaryKey
        private UUID id;

        private PostalAddress postalAddress;

        @CassandraType(type = CassandraType.Name.TIMEUUID)
        private UUID version;

        @Frozen
        private List<String> tags;

        @CassandraType(
                type = CassandraType.Name.MAP,
                typeArguments = {CassandraType.Name.TEXT, CassandraType.Name.INT})
        private Map<String, Integer> scores;

        private LocalDateTime createdAt;
    }

    @UserDefinedType
    private static class PostalAddress {
        private String postalCode;
    }

    @Table
    private static class ConvertedEntity {
        @PrimaryKey
        private UUID id;

        private ExternalReference reference;
    }

    private record ExternalReference(String value) {
    }

    @WritingConverter
    private enum ExternalReferenceWriteConverter implements Converter<ExternalReference, String> {
        INSTANCE;

        @Override
        public String convert(ExternalReference source) {
            return source.value();
        }
    }
}
