package io.github.nguyenductrongdev.automigration.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Shared lifecycle configuration imported by every database provider. */
@Configuration(proxyBeanMethods = false)
public class AutoMigrationCoordinatorConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AutoMigrationCoordinator autoMigrationCoordinator(List<SchemaAutoMigrationProvider> providers) {
        return new AutoMigrationCoordinator(providers);
    }
}
