# Contributing

Thanks for helping improve Spring Schema Auto Migration.

## Safety boundary

Every automatic migration must remain additive and idempotent. Pull requests must not add automatic drop, rename, type change, primary-key change, recreation, data migration, or backfill behavior.

Unsupported differences should be represented as reports, never executable database operations.

## Development

Requirements:

- JDK 21
- Docker for integration tests

Run unit tests and packaging:

```bash
./mvnw clean verify
```

Run the Cassandra Testcontainers suite:

```bash
./mvnw -Pintegration-tests verify
```

Please add focused tests for scanner, comparator, operation ordering, and execution behavior when changing the migration core.
