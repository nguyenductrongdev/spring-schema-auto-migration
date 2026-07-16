package io.github.nguyenductrongdev.automigration.cassandra.config;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.automigration.cassandra.CassandraAutoMigrationProvider;
import io.github.nguyenductrongdev.automigration.cassandra.CassandraAutoMigrationProperties;
import io.github.nguyenductrongdev.automigration.cassandra.CassandraMigrationSession;
import io.github.nguyenductrongdev.automigration.cassandra.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.automigration.cassandra.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.automigration.cassandra.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.automigration.cassandra.log.CassandraMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.cassandra.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.automigration.core.AutoMigrationCoordinatorConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.cassandra.core.convert.CassandraConverter;

import java.util.Objects;

/** Spring beans imported by {@code @EnableCassandraAutoMigration}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CassandraAutoMigrationProperties.class)
@Import(AutoMigrationCoordinatorConfiguration.class)
public class CassandraAutoMigrationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SpringDataCassandraSchemaScanner cassandraMigrationSchemaScanner(CassandraConverter converter) {
        return new SpringDataCassandraSchemaScanner(converter);
    }

    @Bean
    @ConditionalOnMissingBean
    CassandraSchemaInspector cassandraMigrationSchemaInspector() {
        return new CassandraSchemaInspector();
    }

    @Bean
    @ConditionalOnMissingBean
    CassandraSchemaComparator cassandraMigrationSchemaComparator() {
        return new CassandraSchemaComparator();
    }

    @Bean
    @ConditionalOnMissingBean
    CassandraMigrationExecutor cassandraMigrationExecutor() {
        return new CassandraMigrationExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    CassandraMigrationPlanLogger cassandraMigrationPlanLogger() {
        return new CassandraMigrationPlanLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    CassandraAutoMigrationProvider cassandraAutoMigrationProvider(
            CassandraAutoMigrationProperties properties,
            CqlSession applicationSession,
            ObjectProvider<CassandraMigrationSession> migrationSessionProvider,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor,
            CassandraMigrationPlanLogger planLogger) {
        CassandraMigrationSession migrationSession = migrationSessionProvider.getIfAvailable(
                () -> CassandraMigrationSession.of(applicationSession));
        CqlSession session = Objects.requireNonNull(
                migrationSession.cqlSession(), "Cassandra migration session must not be null");
        return new CassandraAutoMigrationProvider(
                properties, session, scanner, inspector, comparator, executor, planLogger);
    }
}