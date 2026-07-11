package io.github.nguyenductrongdev.cassandra.migration.scan;

import io.github.nguyenductrongdev.cassandra.migration.schema.CassandraSchema;
import io.github.nguyenductrongdev.cassandra.migration.schema.ClusteringOrder;
import io.github.nguyenductrongdev.cassandra.migration.schema.TableDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Frozen;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpringDataCassandraSchemaScannerTest {

    private final SpringDataCassandraSchemaScanner scanner =
            new SpringDataCassandraSchemaScanner(new JavaTypeResolver());

    @Test
    void scansTablesReferencedUdtsAndExplicitColumnNames() {
        CassandraSchema schema = scanner.scan(mappingContext(Customer.class, Address.class));

        assertThat(schema.tables()).containsOnlyKeys("customers");
        assertThat(schema.udts()).containsOnlyKeys("address");
        assertThat(schema.tables().get("customers").columns())
                .containsOnlyKeys("id", "email_address", "address");
        assertThat(schema.tables().get("customers").partitionKeyColumns()).containsExactly("id");
        assertThat(schema.udts().get("address").fields()).containsOnlyKeys("street", "postal_code");
    }

    @Test
    void expandsCompositePrimaryKeyClassesInOrdinalOrder() {
        CassandraSchema schema = scanner.scan(mappingContext(CustomerEvent.class, EventKey.class));

        TableDefinition table = schema.tables().get("customerevent");
        assertThat(table.partitionKeyColumns()).containsExactly("tenant_id");
        assertThat(table.clusteringKeyColumns()).containsExactly("occurred_at");
        assertThat(table.clusteringOrders()).containsEntry("occurred_at", ClusteringOrder.DESC);
        assertThat(table.columns()).containsKeys("tenant_id", "occurred_at", "payload", "category");
        assertThat(table.columns().get("category").staticColumn()).isTrue();
        assertThat(table.columns()).doesNotContainKey("key");
    }

    @Test
    void usesSpringDataNamesForDefaultNamedTablesColumnsAndUdts() {
        CassandraSchema schema = scanner.scan(mappingContext(DefaultNamedCustomer.class));

        assertThat(schema.tables()).containsOnlyKeys("defaultnamedcustomer");
        assertThat(schema.udts()).containsOnlyKeys("postaladdress");
        assertThat(schema.tables().get("defaultnamedcustomer").columns())
                .containsOnlyKeys("id", "postaladdress", "version", "tags", "scores");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("postaladdress").cqlType())
                .isEqualTo("frozen<\"postaladdress\">");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("version").cqlType())
                .isEqualTo("timeuuid");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("tags").cqlType())
                .isEqualTo("frozen<list<text>>");
        assertThat(schema.tables().get("defaultnamedcustomer").columns().get("scores").cqlType())
                .isEqualTo("map<text, int>");
        assertThat(schema.udts().get("postaladdress").fields())
                .containsOnlyKeys("postalcode");
    }

    private CassandraMappingContext mappingContext(Class<?>... types) {
        CassandraMappingContext context = new CassandraMappingContext();
        context.setInitialEntitySet(Set.of(types));
        context.afterPropertiesSet();
        return context;
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
    }


    @UserDefinedType
    private static class PostalAddress {
        private String postalCode;
    }
}
