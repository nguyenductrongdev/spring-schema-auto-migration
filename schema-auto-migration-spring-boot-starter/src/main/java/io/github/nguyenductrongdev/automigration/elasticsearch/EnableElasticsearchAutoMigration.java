package io.github.nguyenductrongdev.automigration.elasticsearch;

import io.github.nguyenductrongdev.automigration.elasticsearch.config.ElasticsearchAutoMigrationConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables safe, additive Elasticsearch schema migration during Spring Boot startup. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ElasticsearchAutoMigrationConfiguration.class)
public @interface EnableElasticsearchAutoMigration {
}
