package io.github.nguyenductrongdev.cassandra.migration.sample;

import io.github.nguyenductrongdev.cassandra.migration.EnableCassandraAutoMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCassandraAutoMigration
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
