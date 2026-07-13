package io.github.nguyenductrongdev.automigration.elasticsearch.scan;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchSchemaScannerTest {

    @Test
    void scansDocumentEntitiesInIndexNameOrder() {
        SimpleElasticsearchMappingContext mappingContext = new SimpleElasticsearchMappingContext();
        mappingContext.setInitialEntitySet(Set.of(CustomerDocument.class, AuditDocument.class));
        mappingContext.afterPropertiesSet();
        MappingElasticsearchConverter converter = new MappingElasticsearchConverter(mappingContext);
        converter.afterPropertiesSet();

        ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
        IndexOperations customerIndex = indexOperations(
                Map.of("properties", Map.of("email", Map.of("type", "keyword"))));
        IndexOperations auditIndex = indexOperations(
                Map.of("properties", Map.of("message", Map.of("type", "text"))));
        when(operations.getElasticsearchConverter()).thenReturn(converter);
        when(operations.getIndexCoordinatesFor(CustomerDocument.class))
                .thenReturn(IndexCoordinates.of("customers"));
        when(operations.getIndexCoordinatesFor(AuditDocument.class))
                .thenReturn(IndexCoordinates.of("audit-events"));
        when(operations.indexOps(CustomerDocument.class)).thenReturn(customerIndex);
        when(operations.indexOps(AuditDocument.class)).thenReturn(auditIndex);

        List<ElasticsearchIndexDefinition> definitions =
                new ElasticsearchSchemaScanner().scan(operations);

        assertThat(definitions)
                .extracting(ElasticsearchIndexDefinition::indexName)
                .containsExactly("audit-events", "customers");
        assertThat(definitions.get(1).mapping())
                .containsKey("properties");
    }

    private IndexOperations indexOperations(Map<String, Object> mapping) {
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(indexOperations.createSettings(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Settings(Map.of("index.number_of_shards", "1")));
        when(indexOperations.createMapping(org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.elasticsearch.core.document.Document.from(mapping));
        return indexOperations;
    }

    @Document(indexName = "customers", createIndex = false)
    private static final class CustomerDocument {
        @Id
        private String id;
        @Field(type = FieldType.Keyword)
        private String email;
    }

    @Document(indexName = "audit-events", createIndex = false)
    private static final class AuditDocument {
        @Id
        private String id;
        @Field(type = FieldType.Text)
        private String message;
    }
}
