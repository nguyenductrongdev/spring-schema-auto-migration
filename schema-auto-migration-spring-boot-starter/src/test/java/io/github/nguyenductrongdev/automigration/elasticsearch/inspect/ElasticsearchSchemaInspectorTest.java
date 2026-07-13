package io.github.nguyenductrongdev.automigration.elasticsearch.inspect;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.Settings;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchSchemaInspectorTest {

    private final ElasticsearchSchemaInspector inspector = new ElasticsearchSchemaInspector();

    @Test
    void marksAnAbsentIndexAsMissing() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        ElasticsearchIndexDefinition definition = definition();
        when(operations.indexOps(TestDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);

        List<ElasticsearchIndexSnapshot> snapshots =
                inspector.inspect(operations, List.of(definition));

        assertThat(snapshots).containsExactly(ElasticsearchIndexSnapshot.missing("customers"));
    }

    @Test
    void readsSettingsAndMappingFromAnExistingIndex() {
        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        ElasticsearchIndexDefinition definition = definition();
        Settings settings = new Settings(Map.of("index.number_of_shards", "1"));
        Map<String, Object> mapping =
                Map.of("properties", Map.of("id", Map.of("type", "keyword")));
        when(operations.indexOps(TestDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.getSettings(false)).thenReturn(settings);
        when(indexOperations.getMapping()).thenReturn(mapping);

        List<ElasticsearchIndexSnapshot> snapshots =
                inspector.inspect(operations, List.of(definition));

        assertThat(snapshots).containsExactly(
                new ElasticsearchIndexSnapshot("customers", true, settings, mapping));
    }

    private ElasticsearchIndexDefinition definition() {
        return new ElasticsearchIndexDefinition(
                "customers", TestDocument.class, Map.of(), Map.of());
    }

    private static final class TestDocument {
    }
}
