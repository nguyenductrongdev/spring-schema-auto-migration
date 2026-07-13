package io.github.nguyenductrongdev.automigration.elasticsearch9;

import io.github.nguyenductrongdev.automigration.elasticsearch.EnableElasticsearchAutoMigration;
import io.github.nguyenductrongdev.automigration.elasticsearch.compare.ElasticsearchSchemaComparator;
import io.github.nguyenductrongdev.automigration.elasticsearch.execute.ElasticsearchMigrationExecutor;
import io.github.nguyenductrongdev.automigration.elasticsearch.inspect.ElasticsearchSchemaInspector;
import io.github.nguyenductrongdev.automigration.elasticsearch.scan.ElasticsearchSchemaScanner;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperationType;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = Elasticsearch9AutoMigrationIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "elasticsearch.auto-migration.mode=SAFE_UPDATE")
class Elasticsearch9AutoMigrationIT {

    private static final String INDEX = "migration-customers-es9";

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.2"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.enrollment.enabled", "false");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }

    @Autowired
    private ElasticsearchOperations operations;
    @Autowired
    private ElasticsearchSchemaScanner scanner;
    @Autowired
    private ElasticsearchSchemaInspector inspector;
    @Autowired
    private ElasticsearchSchemaComparator comparator;
    @Autowired
    private ElasticsearchMigrationExecutor executor;

    @Test
    void createsAndAdditivelyUpdatesMappingsIdempotentlyOnElasticsearch9() {
        assertThat(operations.indexOps(CustomerDocument.class).exists()).isTrue();

        List<ElasticsearchIndexDefinition> desired = scanner.scan(operations);
        ElasticsearchIndexDefinition customer = desired.stream()
                .filter(index -> index.indexName().equals(INDEX))
                .findFirst()
                .orElseThrow();
        operations.indexOps(CustomerDocument.class).delete();
        operations.indexOps(CustomerDocument.class).create(
                customer.settings(),
                org.springframework.data.elasticsearch.core.document.Document.from(Map.of(
                        "properties", Map.of())));

        ElasticsearchMigrationPlan plan = comparator.compare(
                desired,
                inspector.inspect(operations, desired));

        assertThat(plan.unsupportedDifferences()).isEmpty();
        assertThat(plan.operations()).singleElement()
                .extracting(operation -> operation.type())
                .isEqualTo(ElasticsearchMigrationOperationType.PUT_MAPPING);
        executor.execute(operations, plan.operations());

        ElasticsearchMigrationPlan idempotentPlan = comparator.compare(
                desired,
                inspector.inspect(operations, desired));
        assertThat(idempotentPlan.isEmpty()).isTrue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableElasticsearchAutoMigration
    @EnableElasticsearchRepositories(considerNestedRepositories = true)
    static class TestApplication {
    }

    interface CustomerRepository extends ElasticsearchRepository<CustomerDocument, String> {
    }

    @Document(indexName = INDEX, createIndex = false)
    static class CustomerDocument {
        @Id
        private String id;
        @Field(type = FieldType.Keyword)
        private String email;
    }
}
