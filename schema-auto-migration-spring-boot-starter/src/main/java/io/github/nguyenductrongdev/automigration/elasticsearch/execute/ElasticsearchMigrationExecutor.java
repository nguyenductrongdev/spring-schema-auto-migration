package io.github.nguyenductrongdev.automigration.elasticsearch.execute;

import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperation;
import io.github.nguyenductrongdev.automigration.elasticsearch.schema.ElasticsearchMigrationOperationType;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import java.util.List;

/** Applies already-validated additive Elasticsearch migration operations in order. */
public class ElasticsearchMigrationExecutor {

    public void execute(
            ElasticsearchOperations operations,
            List<ElasticsearchMigrationOperation> migrationOperations) {
        for (ElasticsearchMigrationOperation migration : migrationOperations) {
            IndexOperations indexOperations = operations.indexOps(migration.entityType());
            boolean acknowledged;
            if (migration.type() == ElasticsearchMigrationOperationType.CREATE_INDEX) {
                acknowledged = indexOperations.create(
                        migration.settings(),
                        Document.from(migration.mapping()));
            } else {
                acknowledged = indexOperations.putMapping(Document.from(migration.mapping()));
            }
            if (!acknowledged) {
                throw new IllegalStateException(
                        "Elasticsearch did not acknowledge " + migration.method() + " " + migration.path());
            }
        }
    }
}
