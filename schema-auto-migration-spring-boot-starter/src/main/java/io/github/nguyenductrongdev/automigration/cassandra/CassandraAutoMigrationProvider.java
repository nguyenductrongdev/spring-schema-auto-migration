package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.automigration.cassandra.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.automigration.cassandra.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.automigration.cassandra.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.automigration.cassandra.log.CassandraMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.cassandra.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CqlNames;
import io.github.nguyenductrongdev.automigration.cassandra.schema.MigrationPlan;
import io.github.nguyenductrongdev.automigration.cassandra.schema.SchemaDifference;
import io.github.nguyenductrongdev.automigration.core.PreparedSchemaMigration;
import io.github.nguyenductrongdev.automigration.core.SchemaAutoMigrationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.util.StringUtils;

/** Cassandra implementation of the shared schema migration provider contract. */
public class CassandraAutoMigrationProvider implements SchemaAutoMigrationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAutoMigrationProvider.class);

    private final CassandraAutoMigrationProperties properties;
    private final CqlSession session;
    private final CassandraMappingContext mappingContext;
    private final SpringDataCassandraSchemaScanner scanner;
    private final CassandraSchemaInspector inspector;
    private final CassandraSchemaComparator comparator;
    private final CassandraMigrationExecutor executor;
    private final CassandraMigrationPlanLogger planLogger;

    public CassandraAutoMigrationProvider(
            CassandraAutoMigrationProperties properties,
            CqlSession session,
            CassandraMappingContext mappingContext,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor,
            CassandraMigrationPlanLogger planLogger) {
        this.properties = properties;
        this.session = session;
        this.mappingContext = mappingContext;
        this.scanner = scanner;
        this.inspector = inspector;
        this.comparator = comparator;
        this.executor = executor;
        this.planLogger = planLogger;
    }

    @Override
    public String providerName() {
        return "cassandra";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public PreparedSchemaMigration prepareMigration() {
        CassandraAutoMigrationMode mode = properties.getMode();
        if (mode == CassandraAutoMigrationMode.NONE) {
            LOGGER.info("Cassandra auto migration is disabled");
            return PreparedSchemaMigration.noOp();
        }

        String keyspace = resolveKeyspace();
        session.refreshSchema();
        CassandraSchema existing = inspector.inspect(session, keyspace);
        CassandraSchema desired = scanner.scan(mappingContext);
        MigrationPlan plan = comparator.compare(keyspace, desired, existing);
        return new PreparedCassandraMigration(mode, keyspace, plan);
    }

    private final class PreparedCassandraMigration implements PreparedSchemaMigration {

        private final CassandraAutoMigrationMode mode;
        private final String keyspace;
        private final MigrationPlan plan;

        private PreparedCassandraMigration(
                CassandraAutoMigrationMode mode,
                String keyspace,
                MigrationPlan plan) {
            this.mode = mode;
            this.keyspace = keyspace;
            this.plan = plan;
        }

        @Override
        public void validate() {
            if (mode == CassandraAutoMigrationMode.DRY_RUN) {
                planLogger.log(keyspace, plan);
            } else {
                reportUnsupported(plan);
            }
            failIfUnsupported(plan);
        }

        @Override
        public void execute() {
            if (mode != CassandraAutoMigrationMode.SAFE_UPDATE) {
                return;
            }

            failIfUnsupported(plan);
            if (plan.isEmpty()) {
                LOGGER.info("Cassandra schema for keyspace {} is up to date", CqlNamesForLog.quote(keyspace));
                return;
            }
            if (plan.supportedCql().isEmpty()) {
                LOGGER.info("No supported Cassandra migration statements were generated for keyspace {}",
                        CqlNamesForLog.quote(keyspace));
                return;
            }

            plan.supportedCql().forEach(cql -> LOGGER.info("Cassandra migration CQL: {};", cql));
            executor.execute(session, plan.supportedCql());
            LOGGER.info("Applied {} additive Cassandra migration statement(s) to keyspace {}",
                    plan.supportedCql().size(), CqlNamesForLog.quote(keyspace));
        }
    }

    private void failIfUnsupported(MigrationPlan plan) {
        if (!plan.unsupportedDifferences().isEmpty()) {
            throw new IllegalStateException("Cassandra schema contains "
                    + plan.unsupportedDifferences().size() + " unsupported difference(s); no migration was executed");
        }
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
