package io.github.nguyenductrongdev.automigration.cassandra.sample;

import io.github.nguyenductrongdev.automigration.cassandra.EnableCassandraAutoMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCassandraAutoMigration
public class CassandraSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(CassandraSampleApplication.class, args);
    }
}
