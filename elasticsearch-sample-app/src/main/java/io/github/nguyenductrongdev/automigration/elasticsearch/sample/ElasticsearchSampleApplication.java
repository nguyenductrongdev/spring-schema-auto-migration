package io.github.nguyenductrongdev.automigration.elasticsearch.sample;

import io.github.nguyenductrongdev.automigration.elasticsearch.EnableElasticsearchAutoMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableElasticsearchAutoMigration
public class ElasticsearchSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElasticsearchSampleApplication.class, args);
    }
}
