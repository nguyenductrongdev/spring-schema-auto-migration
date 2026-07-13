package io.github.nguyenductrongdev.automigration.elasticsearch.scan;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.ElasticsearchPersistentEntity;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Scans Spring Data Elasticsearch document entities into deterministic index definitions. */
public class ElasticsearchSchemaScanner {

    public List<ElasticsearchIndexDefinition> scan(ElasticsearchOperations operations) {
        List<ElasticsearchIndexDefinition> definitions = new ArrayList<>();
        Set<String> indexNames = new HashSet<>();

        for (ElasticsearchPersistentEntity<?> entity
                : operations.getElasticsearchConverter().getMappingContext().getPersistentEntities()) {
            if (!entity.isAnnotationPresent(Document.class)) {
                continue;
            }

            Class<?> entityType = entity.getType();
            IndexCoordinates coordinates = operations.getIndexCoordinatesFor(entityType);
            validateCoordinates(entityType, coordinates);
            String indexName = coordinates.getIndexName();
            if (!indexNames.add(indexName)) {
                throw new IllegalStateException(
                        "Multiple Elasticsearch document entities resolve to index '" + indexName + "'");
            }

            IndexOperations indexOperations = operations.indexOps(entityType);
            definitions.add(new ElasticsearchIndexDefinition(
                    indexName,
                    entityType,
                    indexOperations.createSettings(entityType),
                    indexOperations.createMapping(entityType)));
        }

        definitions.sort((left, right) -> left.indexName().compareTo(right.indexName()));
        return List.copyOf(definitions);
    }

    private void validateCoordinates(Class<?> entityType, IndexCoordinates coordinates) {
        String[] names = coordinates.getIndexNames();
        if (names.length != 1 || !StringUtils.hasText(names[0])) {
            throw new IllegalStateException(
                    "Elasticsearch entity " + entityType.getName() + " must resolve to exactly one index");
        }
        if (names[0].contains("*") || names[0].contains(",")) {
            throw new IllegalStateException(
                    "Elasticsearch entity " + entityType.getName()
                            + " resolves to a wildcard or multi-index expression, which cannot be migrated safely: "
                            + names[0]);
        }
    }
}
