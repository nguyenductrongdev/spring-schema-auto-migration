# Contributing

Thanks for helping improve Spring Schema Auto Migration.

## Safety boundary

Every automatic migration must remain additive and idempotent. Pull requests must not add automatic drop, rename, type change, primary-key change, clustering-order mutation, recreation, data migration, backfill, or keyspace replication behavior.

Unsupported differences must remain reportable validation failures, never executable database operations. All providers must finish validation before any provider begins execution.

## Development requirements

- JDK 17 or newer
- Docker for Cassandra integration tests
- The included Maven Wrapper

Build the Java 17 baseline:

```bash
./mvnw clean verify
```

Install the artifacts and test an independent consumer against another Boot line:

```bash
./mvnw clean install
./mvnw -f compatibility-tests/pom.xml -Dspring-boot.version=4.1.0 clean verify
```

Run Cassandra integration tests:

```bash
./mvnw -Pintegration-tests -Dcassandra.test.image=cassandra:5.0.8 verify
```

The `compatibility-tests` project intentionally does not inherit the repository parent and is not part of the reactor. Keep it representative of a real consumer application.

Add focused tests for mapping, comparison, operation ordering, schema agreement, lifecycle ordering, and failure behavior when changing migration logic. Changes to supported Spring Boot or Cassandra lines must update the CI matrix and README compatibility table in the same pull request.

## Security

Do not commit database credentials, tokens, certificates, private endpoints, or production schema dumps. Report security findings privately according to [SECURITY.md](SECURITY.md).