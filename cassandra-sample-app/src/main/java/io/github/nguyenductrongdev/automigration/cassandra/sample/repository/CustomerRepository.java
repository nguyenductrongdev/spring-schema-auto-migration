package io.github.nguyenductrongdev.automigration.cassandra.sample.repository;

import io.github.nguyenductrongdev.automigration.cassandra.sample.domain.Customer;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

public interface CustomerRepository extends CassandraRepository<Customer, UUID> {
}
