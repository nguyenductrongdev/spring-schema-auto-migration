package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.automigration.cassandra.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.automigration.cassandra.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.automigration.cassandra.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ClusteringOrder;
import io.github.nguyenductrongdev.automigration.cassandra.schema.ColumnDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.TableDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtDefinition;
import io.github.nguyenductrongdev.automigration.cassandra.schema.UdtFieldDefinition;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CassandraAutoMigrationIT {

    private static final String LOCAL_DATACENTER = "datacenter1";
    private static final int CQL_PORT = 9042;
    private static final String KEYSPACE = "migration_it";

    @Container
    static final GenericContainer<?> CASSANDRA =
            new GenericContainer<>(DockerImageName.parse(
                    System.getProperty("cassandra.test.image", "cassandra:5.0.8")))
                    .withExposedPorts(CQL_PORT)
                    .withEnv("CASSANDRA_SNITCH", "GossipingPropertyFileSnitch")
                    .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
                    .withEnv("CASSANDRA_DC", LOCAL_DATACENTER)
                    .withEnv(
                            "JVM_OPTS",
                            "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=0")
                    .withEnv("HEAP_NEWSIZE", "128M")
                    .withEnv("MAX_HEAP_SIZE", "1024M")
                    .waitingFor(new CassandraQueryWaitStrategy());

    private final CassandraSchemaInspector inspector = new CassandraSchemaInspector();
    private final CassandraSchemaComparator comparator = new CassandraSchemaComparator();
    private final CassandraMigrationExecutor executor = new CassandraMigrationExecutor();

    @Test
    void appliesCreateAndAlterMigrationsIdempotently() {
        try (CqlSession bootstrap = newSession(null)) {
            bootstrap.execute("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
                    + " WITH replication = {'class': 'NetworkTopologyStrategy', '"
                    + LOCAL_DATACENTER + "': 1}");
        }

        try (CqlSession session = newSession(KEYSPACE)) {
            CassandraSchema initial = schema(false);
            MigrationPlan createPlan = comparator.compare(
                    KEYSPACE, initial, inspector.inspect(session, KEYSPACE));

            assertThat(createPlan.supportedCql()).hasSize(3);
            assertThat(createPlan.supportedCql()).anyMatch(statement -> statement.endsWith(
                    "WITH CLUSTERING ORDER BY (\"occurred_at\" DESC)"));
            assertThat(createPlan.unsupportedDifferences()).isEmpty();
            executor.execute(session, createPlan.supportedCql());
            session.refreshSchema();

            assertThat(comparator.compare(
                    KEYSPACE, initial, inspector.inspect(session, KEYSPACE)).isEmpty()).isTrue();

            CassandraSchema evolved = schema(true);
            MigrationPlan alterPlan = comparator.compare(
                    KEYSPACE, evolved, inspector.inspect(session, KEYSPACE));

            assertThat(alterPlan.supportedCql()).hasSize(2);
            assertThat(alterPlan.supportedCql())
                    .anyMatch(statement -> statement.startsWith("ALTER TYPE"))
                    .anyMatch(statement -> statement.startsWith("ALTER TABLE"));
            executor.execute(session, alterPlan.supportedCql());
            session.refreshSchema();

            assertThat(comparator.compare(
                    KEYSPACE, evolved, inspector.inspect(session, KEYSPACE)).isEmpty()).isTrue();
        }
    }

    private CqlSession newSession(String keyspace) {
        var builder = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(
                        CASSANDRA.getHost(), CASSANDRA.getMappedPort(CQL_PORT)))
                .withLocalDatacenter(LOCAL_DATACENTER);
        if (keyspace != null) {
            builder.withKeyspace(keyspace);
        }
        return builder.build();
    }

    private CassandraSchema schema(boolean evolved) {
        Map<String, UdtFieldDefinition> addressFields = new LinkedHashMap<>();
        addressFields.put("street", new UdtFieldDefinition("street", "text"));
        if (evolved) {
            addressFields.put("postal_code", new UdtFieldDefinition("postal_code", "text"));
        }
        UdtDefinition address = new UdtDefinition("address", addressFields);

        Map<String, ColumnDefinition> customerColumns = new LinkedHashMap<>();
        customerColumns.put("id", new ColumnDefinition("id", "uuid", true));
        customerColumns.put("address", new ColumnDefinition("address", "frozen<\"address\">", false));
        if (evolved) {
            customerColumns.put("email", new ColumnDefinition("email", "text", false));
        }
        TableDefinition customer = new TableDefinition(
                "customer", customerColumns, List.of("id"), List.of());

        Map<String, ColumnDefinition> eventColumns = new LinkedHashMap<>();
        eventColumns.put("tenant_id", new ColumnDefinition("tenant_id", "uuid", true));
        eventColumns.put("occurred_at", new ColumnDefinition("occurred_at", "timestamp", true));
        eventColumns.put("payload", new ColumnDefinition("payload", "text", false));
        TableDefinition customerEvent = new TableDefinition(
                "customer_event",
                eventColumns,
                List.of("tenant_id"),
                List.of("occurred_at"),
                Map.of("occurred_at", ClusteringOrder.DESC));

        return new CassandraSchema(
                Map.of(customer.name(), customer, customerEvent.name(), customerEvent),
                Map.of(address.name(), address));
    }

    private static final class CassandraQueryWaitStrategy extends AbstractWaitStrategy {

        @Override
        protected void waitUntilReady() {
            Instant deadline = Instant.now().plus(startupTimeout);
            RuntimeException lastFailure = null;

            while (Instant.now().isBefore(deadline)) {
                try (CqlSession session = CqlSession.builder()
                        .addContactPoint(new InetSocketAddress(
                                waitStrategyTarget.getHost(),
                                waitStrategyTarget.getMappedPort(CQL_PORT)))
                        .withLocalDatacenter(LOCAL_DATACENTER)
                        .build()) {
                    session.execute("SELECT release_version FROM system.local");
                    return;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    pauseBeforeRetry();
                }
            }

            throw new ContainerLaunchException(
                    "Timed out waiting for Cassandra to be accessible for query execution",
                    lastFailure);
        }

        private void pauseBeforeRetry() {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ContainerLaunchException(
                        "Interrupted while waiting for Cassandra to become ready",
                        exception);
            }
        }
    }
}
