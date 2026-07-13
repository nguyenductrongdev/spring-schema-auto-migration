package io.github.nguyenductrongdev.automigration.elasticsearch.config;

import io.github.nguyenductrongdev.automigration.core.AutoMigrationCoordinatorConfiguration;
import io.github.nguyenductrongdev.automigration.elasticsearch.ElasticsearchAutoMigrationProperties;
import io.github.nguyenductrongdev.automigration.elasticsearch.ElasticsearchAutoMigrationProvider;
import io.github.nguyenductrongdev.automigration.elasticsearch.compare.ElasticsearchSchemaComparator;
import io.github.nguyenductrongdev.automigration.elasticsearch.execute.ElasticsearchMigrationExecutor;
import io.github.nguyenductrongdev.automigration.elasticsearch.inspect.ElasticsearchSchemaInspector;
import io.github.nguyenductrongdev.automigration.elasticsearch.log.ElasticsearchMigrationPlanLogger;
import io.github.nguyenductrongdev.automigration.elasticsearch.scan.ElasticsearchSchemaScanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/** Spring beans imported by {@code @EnableElasticsearchAutoMigration}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ElasticsearchAutoMigrationProperties.class)
@Import(AutoMigrationCoordinatorConfiguration.class)
public class ElasticsearchAutoMigrationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchSchemaScanner elasticsearchMigrationSchemaScanner() {
        return new ElasticsearchSchemaScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchSchemaInspector elasticsearchMigrationSchemaInspector() {
        return new ElasticsearchSchemaInspector();
    }

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchSchemaComparator elasticsearchMigrationSchemaComparator() {
        return new ElasticsearchSchemaComparator();
    }

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchMigrationExecutor elasticsearchMigrationExecutor() {
        return new ElasticsearchMigrationExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchMigrationPlanLogger elasticsearchMigrationPlanLogger() {
        return new ElasticsearchMigrationPlanLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    ElasticsearchAutoMigrationProvider elasticsearchAutoMigrationProvider(
            ElasticsearchAutoMigrationProperties properties,
            ElasticsearchOperations operations,
            ElasticsearchSchemaScanner scanner,
            ElasticsearchSchemaInspector inspector,
            ElasticsearchSchemaComparator comparator,
            ElasticsearchMigrationExecutor executor,
            ElasticsearchMigrationPlanLogger planLogger) {
        return new ElasticsearchAutoMigrationProvider(
                properties, operations, scanner, inspector, comparator, executor, planLogger);
    }
}
