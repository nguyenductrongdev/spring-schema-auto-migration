package io.github.nguyenductrongdev.automigration.elasticsearch.sample.repository;

import io.github.nguyenductrongdev.automigration.elasticsearch.sample.domain.CustomerDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CustomerSearchRepository extends ElasticsearchRepository<CustomerDocument, String> {
}
