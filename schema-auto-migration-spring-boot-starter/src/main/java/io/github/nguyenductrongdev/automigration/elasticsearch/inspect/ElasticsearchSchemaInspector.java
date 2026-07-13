package io.github.nguyenductrongdev.automigration.elasticsearch.inspect;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexDefinition;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchIndexSnapshot;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.ArrayList;
import java.util.List;

/** Reads the current state of every index managed by a Spring Data document entity. */
public class ElasticsearchSchemaInspector {

    public List<ElasticsearchIndexSnapshot> inspect(
            ElasticsearchOperations operations,
            List<ElasticsearchIndexDefinition> desiredIndexes) {
        List<ElasticsearchIndexSnapshot> snapshots = new ArrayList<>();
        for (ElasticsearchIndexDefinition desired : desiredIndexes) {
            IndexOperations indexOperations = operations.indexOps(desired.entityType());
            if (!indexOperations.exists()) {
                snapshots.add(ElasticsearchIndexSnapshot.missing(desired.indexName()));
                continue;
            }
            snapshots.add(new ElasticsearchIndexSnapshot(
                    desired.indexName(),
                    true,
                    indexOperations.getSettings(false),
                    indexOperations.getMapping()));
        }
        return List.copyOf(snapshots);
    }
}
