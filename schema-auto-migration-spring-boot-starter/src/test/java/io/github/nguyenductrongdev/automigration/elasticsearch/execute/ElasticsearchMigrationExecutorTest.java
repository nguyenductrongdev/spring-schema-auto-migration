package io.github.nguyenductrongdev.automigration.elasticsearch.execute;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchMigrationExecutorTest {

    private final ElasticsearchMigrationExecutor executor = new ElasticsearchMigrationExecutor();

    @Test
    void createsAnIndexAndAddsMappingsUsingSpringDataOperations() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        ElasticsearchIndexDefinition definition = definition();
        when(operations.indexOps(TestDocument.class)).thenReturn(indexOperations);
        when(indexOperations.create(eq(definition.settings()), any(Document.class))).thenReturn(true);
        when(indexOperations.putMapping(any(Document.class))).thenReturn(true);

        executor.execute(operations, List.of(
                ElasticsearchMigrationOperation.createIndex(definition),
                ElasticsearchMigrationOperation.putMapping(definition)));

        verify(indexOperations).create(eq(definition.settings()), eq(Document.from(definition.mapping())));
        verify(indexOperations).putMapping(eq(Document.from(definition.mapping())));
    }

    @Test
    void failsWhenElasticsearchDoesNotAcknowledgeAnOperation() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        ElasticsearchIndexDefinition definition = definition();
        when(operations.indexOps(TestDocument.class)).thenReturn(indexOperations);
        when(indexOperations.putMapping(any(Document.class))).thenReturn(false);

        assertThatThrownBy(() -> executor.execute(
                operations,
                List.of(ElasticsearchMigrationOperation.putMapping(definition))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUT /customers/_mapping");
    }

    private ElasticsearchIndexDefinition definition() {
        return new ElasticsearchIndexDefinition(
                "customers",
                TestDocument.class,
                Map.of("index.number_of_shards", "1"),
                Map.of("properties", Map.of("id", Map.of("type", "keyword"))));
    }

    private static final class TestDocument {
    }
}
