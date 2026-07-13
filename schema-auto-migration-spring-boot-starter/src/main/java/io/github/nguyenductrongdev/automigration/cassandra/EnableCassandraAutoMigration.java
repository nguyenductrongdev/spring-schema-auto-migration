package io.github.nguyenductrongdev.automigration.cassandra;

import io.github.nguyenductrongdev.automigration.cassandra.config.CassandraAutoMigrationConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables safe, additive Cassandra schema migration during Spring Boot startup. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CassandraAutoMigrationConfiguration.class)
public @interface EnableCassandraAutoMigration {
}

