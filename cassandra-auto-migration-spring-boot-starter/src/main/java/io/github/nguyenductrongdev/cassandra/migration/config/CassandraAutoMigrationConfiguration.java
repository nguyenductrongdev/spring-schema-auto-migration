package io.github.nguyenductrongdev.cassandra.migration.config;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.cassandra.migration.CassandraAutoMigrationProperties;
import io.github.nguyenductrongdev.cassandra.migration.CassandraAutoMigrationRunner;
import io.github.nguyenductrongdev.cassandra.migration.compare.CassandraSchemaComparator;
import io.github.nguyenductrongdev.cassandra.migration.execute.CassandraMigrationExecutor;
import io.github.nguyenductrongdev.cassandra.migration.inspect.CassandraSchemaInspector;
import io.github.nguyenductrongdev.cassandra.migration.scan.JavaTypeResolver;
import io.github.nguyenductrongdev.cassandra.migration.scan.SpringDataCassandraSchemaScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;

/** Spring beans imported by {@code @EnableCassandraAutoMigration}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CassandraAutoMigrationProperties.class)
public class CassandraAutoMigrationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JavaTypeResolver cassandraMigrationJavaTypeResolver() {
        return new JavaTypeResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    SpringDataCassandraSchemaScanner cassandraMigrationSchemaScanner(JavaTypeResolver typeResolver) {
        return new SpringDataCassandraSchemaScanner(typeResolver);
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
    CassandraAutoMigrationRunner cassandraAutoMigrationRunner(
            CassandraAutoMigrationProperties properties,
            CqlSession session,
            CassandraMappingContext mappingContext,
            SpringDataCassandraSchemaScanner scanner,
            CassandraSchemaInspector inspector,
            CassandraSchemaComparator comparator,
            CassandraMigrationExecutor executor) {
        return new CassandraAutoMigrationRunner(
                properties, session, mappingContext, scanner, inspector, comparator, executor);
    }
}
