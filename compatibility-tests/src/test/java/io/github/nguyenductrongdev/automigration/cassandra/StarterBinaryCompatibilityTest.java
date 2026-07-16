package io.github.nguyenductrongdev.automigration.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.nguyenductrongdev.automigration.cassandra.scan.SpringDataCassandraSchemaScanner;
import io.github.nguyenductrongdev.automigration.cassandra.schema.CassandraSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class StarterBinaryCompatibilityTest {

    @Test
    void recognizesTheRuntimeSpringBootLine() {
        assertThatCode(SpringBootCompatibility::verifySupported).doesNotThrowAnyException();
    }

    @Test
    void loadsStarterConfigurationWithBootManagedDependencies() {
        new ApplicationContextRunner()
                .withUserConfiguration(EnabledConfiguration.class)
                .withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, () -> converter(Book.class))
                .withPropertyValues("schema-auto-migration.cassandra.mode=NONE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CassandraAutoMigrationProvider.class);
                });
    }

    @Test
    void scansSpringDataMetadataWithoutBinaryLinkageErrors() {
        CassandraSchema schema = new SpringDataCassandraSchemaScanner(converter(Book.class)).scan();

        assertThat(schema.tables()).containsOnlyKeys("books");
        assertThat(schema.tables().get("books").columns()).containsOnlyKeys("id", "title");
        assertThat(schema.tables().get("books").columns().get("id").cqlType()).isEqualTo("uuid");
        assertThat(schema.tables().get("books").columns().get("title").cqlType()).isEqualTo("text");
    }

    private static MappingCassandraConverter converter(Class<?>... entityTypes) {
        CassandraMappingContext context = new CassandraMappingContext();
        context.setInitialEntitySet(Set.of(entityTypes));
        context.afterPropertiesSet();

        MappingCassandraConverter converter = new MappingCassandraConverter(context);
        converter.afterPropertiesSet();
        return converter;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCassandraAutoMigration
    static class EnabledConfiguration {
    }

    @Table("books")
    static class Book {

        @PrimaryKey
        private UUID id;

        private String title;
    }
}