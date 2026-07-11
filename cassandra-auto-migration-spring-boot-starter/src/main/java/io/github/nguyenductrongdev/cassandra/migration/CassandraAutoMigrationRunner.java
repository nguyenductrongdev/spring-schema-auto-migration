package io.github.nguyenductrongdev.cassandra.migration;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.cassandra.migration.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.cassandra.migration.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.cassandra.migration.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.cassandra.migration.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.cassandra.migration.schema.CassandraSchema;
import io.github.nguyenductrongdev.cassandra.migration.schema.CqlNames;
import io.github.nguyenductrongdev.cassandra.migration.schema.MigrationPlan;
import io.github.nguyenductrongdev.cassandra.migration.schema.SchemaDifference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.util.StringUtils;

/** Coordinates schema scanning, comparison, reporting and optional execution. */
public class CassandraAutoMigrationRunner implements ApplicationRunner, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAutoMigrationRunner.class);

    private final CassandraAutoMigrationProperties properties;
    private final CqlSession session;
    private final CassandraMappingContext mappingContext;
    private final SpringDataCassandraSchemaScanner scanner;
    private final CassandraSchemaInspector inspector;
    private final CassandraSchemaComparator comparator;
    private final CassandraMigrationExecutor executor;

    public CassandraAutoMigrationRunner(
            CassandraAutoMigrationProperties properties,
            CqlSession session,
            CassandraMappingContext mappingContext,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor) {
        this.properties = properties;
        this.session = session;
        this.mappingContext = mappingContext;
        this.scanner = scanner;
        this.inspector = inspector;
        this.comparator = comparator;
        this.executor = executor;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        CassandraAutoMigrationMode mode = properties.getMode();
        if (mode == CassandraAutoMigrationMode.NONE) {
            LOGGER.info("Cassandra auto migration is disabled");
            return;
        }

        String keyspace = resolveKeyspace();
        session.refreshSchema();
        CassandraSchema desired = scanner.scan(mappingContext);
        CassandraSchema existing = inspector.inspect(session, keyspace);
        MigrationPlan plan = comparator.compare(
                keyspace, desired, existing, properties.isReportUnmanagedObjects());

        if (plan.isEmpty()) {
            LOGGER.info("Cassandra schema for keyspace {} is up to date", CqlNamesForLog.quote(keyspace));
            return;
        }

        reportUnsupported(plan);
        if (properties.isFailOnUnsupported() && !plan.unsupportedDifferences().isEmpty()) {
            throw new IllegalStateException("Cassandra schema contains "
                    + plan.unsupportedDifferences().size() + " unsupported difference(s); no migration was executed");
        }

        if (mode == CassandraAutoMigrationMode.VALIDATE) {
            LOGGER.warn("Cassandra schema validation found {} additive migration(s) and {} unsupported difference(s)",
                    plan.supportedCql().size(), plan.unsupportedDifferences().size());
            return;
        }

        plan.supportedCql().forEach(cql -> LOGGER.info("Cassandra migration CQL: {};", cql));
        if (mode == CassandraAutoMigrationMode.SCRIPT) {
            LOGGER.info("SCRIPT mode generated {} statement(s); nothing was executed", plan.supportedCql().size());
            return;
        }

        executor.execute(session, plan.supportedCql());
        LOGGER.info("Applied {} additive Cassandra migration statement(s) to keyspace {}",
                plan.supportedCql().size(), CqlNamesForLog.quote(keyspace));
    }

    private String resolveKeyspace() {
        if (StringUtils.hasText(properties.getKeyspaceName())) {
            return CqlNames.internal(properties.getKeyspaceName());
        }
        return session.getKeyspace()
                .map(CqlIdentifier::asInternal)
                .orElseThrow(() -> new IllegalStateException(
                        "No Cassandra keyspace is configured. Set spring.cassandra.keyspace-name "
                                + "or cassandra.auto-migration.keyspace-name"));
    }

    private void reportUnsupported(MigrationPlan plan) {
        for (SchemaDifference difference : plan.unsupportedDifferences()) {
            LOGGER.warn("Unsupported Cassandra schema difference [{}] {}: {}",
                    difference.type(), difference.objectName(), difference.message());
        }
    }

    private static final class CqlNamesForLog {
        private CqlNamesForLog() {
        }

        static String quote(String name) {
            return "'" + name.replace("'", "''") + "'";
        }
    }
}

