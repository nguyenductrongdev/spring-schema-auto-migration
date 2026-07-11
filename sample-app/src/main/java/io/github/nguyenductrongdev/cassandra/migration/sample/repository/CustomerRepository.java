package io.github.nguyenductrongdev.cassandra.migration.sample.repository;

import io.github.nguyenductrongdev.cassandra.migration.sample.domain.Customer;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

public interface CustomerRepository extends CassandraRepository<Customer, UUID> {
}
