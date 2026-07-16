# Compatibility Tests

This standalone Maven project verifies the installed Cassandra starter as an external Spring Boot consumer.

It deliberately has no parent and is not included in the repository reactor. That prevents reactor classpaths and inherited dependency management from hiding published-POM or binary compatibility problems.

Install the repository artifacts first, then select a supported Boot line:

```bash
./mvnw clean install
./mvnw -f compatibility-tests/pom.xml -Dspring-boot.version=4.1.0 clean verify
```

The CI matrix runs this project on every supported Spring Boot line and at the tested Java compatibility endpoints.