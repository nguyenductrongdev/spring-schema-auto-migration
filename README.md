# Spring Schema Auto Migration

[![CI](https://github.com/nguyenductrongdev/spring-schema-auto-migration/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenductrongdev/spring-schema-auto-migration/actions/workflows/ci.yml)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](#compatibility)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Safe, additive Cassandra schema evolution for Spring Boot applications, derived from Spring Data mappings.

Spring Schema Auto Migration compares the application's mapped schema with the live Cassandra keyspace during startup. It can log or apply a deterministic set of supported additive changes, and rejects destructive or ambiguous differences before execution.

The Cassandra provider is explicitly enabled with `@EnableCassandraAutoMigration`. Adding the dependency alone does not start schema scanning or migration.

> **Project status:** `0.1.0-SNAPSHOT` is a pre-release build. Public APIs may change before `1.0.0`.

## Features

- Additive-only Cassandra schema changes
- `NONE`, `DRY_RUN`, and `SAFE_UPDATE` execution modes
- Schema discovery from Spring Data Cassandra mapping metadata
- UDT, table, regular column, static column, and clustering-order support
- Global validation before any enabled provider starts execution
- Sequential Cassandra DDL with schema-agreement checks
- Java 17 bytecode with Spring Boot 3.5 and 4.x support from one starter
- No migration files, generated reports, or schema history table

## Compatibility

| Component | Supported versions |
| --- | --- |
| Spring Boot | `3.5.x`, `4.0.x`, `4.1.x` |
| Java | 17 or later, within the range supported by the selected Spring Boot version |
| Apache Cassandra | `4.1.x`, `5.0.x` |

Artifacts are compiled with `--release 17`. The application's Spring Boot parent or imported BOM controls the Spring Data Cassandra and Cassandra Java Driver versions. The starter does not require a separate artifact for Spring Boot 3 and Spring Boot 4.

See the [CI workflow](.github/workflows/ci.yml) for the tested compatibility matrix.

## Installation

SNAPSHOT artifacts are published to GitHub Packages. Configure the package repository and add the Cassandra starter:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/nguyenductrongdev/spring-schema-auto-migration</url>
        <releases>
            <enabled>true</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.nguyenductrongdev</groupId>
        <artifactId>schema-auto-migration-cassandra-spring-boot-starter</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

The starter includes `spring-boot-starter-data-cassandra` transitively. Applications do not need to declare it a second time or change their existing Spring Boot parent.

GitHub Packages requires authenticated Maven access. Configure a Maven server named `github` using [docs/settings.xml](docs/settings.xml), then provide a token with `read:packages` through the environment:

```bash
export GITHUB_TOKEN="<token-with-read-packages>"
```

PowerShell:

```powershell
$env:GITHUB_TOKEN = "<token-with-read-packages>"
```

Use `mvn -U` when Maven must refresh a timestamped SNAPSHOT. Never commit package tokens or Maven settings containing literal credentials.

The optional `schema-auto-migration-bom` aligns versions when multiple library modules are used. It is not required for the single starter dependency above.

## Quick Start

### 1. Enable the Cassandra provider

Add `@EnableCassandraAutoMigration` to the Spring Boot application:

```java
package com.example.application;

import io.github.nguyenductrongdev.automigration.cassandra.EnableCassandraAutoMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCassandraAutoMigration
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. Configure Cassandra and migration mode

Connection settings use Spring Boot's standard `spring.cassandra.*` properties. Only the migration mode is provider-specific:

```yaml
spring:
  cassandra:
    contact-points: ${CASSANDRA_CONTACT_POINTS:localhost}
    port: ${CASSANDRA_PORT:9042}
    local-datacenter: ${CASSANDRA_LOCAL_DATACENTER:datacenter1}
    keyspace-name: ${CASSANDRA_KEYSPACE:application_keyspace}
    username: ${CASSANDRA_USERNAME}
    password: ${CASSANDRA_PASSWORD}
    schema-action: none

schema-auto-migration:
  cassandra:
    mode: ${CASSANDRA_MIGRATION_MODE:DRY_RUN}
```

Remove `username` and `password` only for clusters configured without authentication. TLS, contact points, credentials, datacenter, and keyspace remain owned by Spring Boot's Cassandra configuration.

The selected keyspace must already exist. The provider never creates keyspaces or changes replication settings. A missing or inaccessible keyspace aborts application startup.

### 3. Review and apply the plan

| Mode | Behavior |
| --- | --- |
| `NONE` | Default. Skips scanning, comparison, and migration |
| `DRY_RUN` | Logs the complete ordered plan; executes no CQL and writes no report file |
| `SAFE_UPDATE` | Validates the complete plan, then executes supported additive CQL |

A recommended rollout starts with `DRY_RUN`. Review the logger `io.github.nguyenductrongdev.automigration.plan.cassandra`, then explicitly switch to `SAFE_UPDATE`.

Unsupported differences fail startup in both `DRY_RUN` and `SAFE_UPDATE`. In `DRY_RUN`, the complete plan is logged before the failure is raised.

## Safety Model

### Applied automatically

| Detected difference | Generated action |
| --- | --- |
| Missing UDT | `CREATE TYPE IF NOT EXISTS` |
| Missing UDT field | `ALTER TYPE ... ADD IF NOT EXISTS` |
| Missing table | `CREATE TABLE IF NOT EXISTS`, including primary key and clustering order |
| Missing non-key column | `ALTER TABLE ... ADD IF NOT EXISTS`, including static columns |

Generated statements are additive and idempotent. UDTs are created before tables that reference them, and UDT fields are added before dependent table columns.

### Rejected at startup

The provider reports these differences and executes no migration plan:

- Existing table or UDT is not represented by an application mapping
- Existing column or UDT field is absent from the mapped schema
- Column or UDT field type differs
- Partition key or clustering key differs
- Clustering order differs
- Column kind differs between regular and static

Renames are not inferred. A rename appears as a new mapped object plus an unmanaged existing object, so validation fails instead of attempting a data move or destructive operation.

### Outside the managed scope

The provider does not create, alter, or reconcile:

- Keyspaces and replication settings
- Secondary indexes and custom indexes
- Materialized views
- Table options
- Data migrations and backfills
- Manual migration scripts, version history, or checksums

Use reviewed, explicit migrations for changes outside this scope.

## Mapping Support

The scanner uses the active Spring Data Cassandra `CassandraConverter` and public schema metadata APIs. Supported mapping features include:

- `@Table` and `@UserDefinedType`
- `@PrimaryKey`, `@PrimaryKeyClass`, and `@PrimaryKeyColumn`
- Clustering `ASC` and `DESC` ordering
- `@Column`, including static columns
- `@CassandraType`, `@Frozen`, and `@VectorType`
- `@Transient`
- Scalars, enums, arrays, `Optional`, collections, maps, and mapped UDTs
- Application custom writing converters registered with Spring Data Cassandra

`@VectorType` requires a Cassandra server and driver combination that supports vectors. Unknown Java types fail explicitly rather than being mapped by guesswork.

## Startup Lifecycle

1. Spring creates and initializes non-lazy singleton beans, including the normal Boot data-initialization path.
2. Enabled providers scan mappings, inspect their databases, and prepare plans concurrently on a bounded executor.
3. The coordinator waits for all plans and validates every provider before any provider executes.
4. Valid providers execute concurrently. Operations within the Cassandra provider remain sequential.
5. Cassandra schema agreement is checked after every DDL statement.
6. Spring lifecycle components can start accepting traffic only after all enabled migrations finish.

Planning, validation, or execution failure aborts application-context startup. Validation prevents known unsupported plans from causing partial work, but execution across different databases cannot be globally transactional.

## Dedicated DDL Credentials

By default, migrations use Spring Boot's application `CqlSession`, including its contact points, datacenter, keyspace, TLS, username, and password.

Applications that separate DML and DDL privileges can register one `CassandraMigrationSession` bean. Do not register a second raw `CqlSession` bean, because that can replace Spring Boot's application-session auto-configuration.

- Use `CassandraMigrationSession.owned(session)` when Spring should close the dedicated session.
- Use `CassandraMigrationSession.of(session)` when another component owns the session lifecycle.

The selected session must have a configured keyspace and the permissions required for schema inspection and supported `CREATE` or `ALTER` operations. Keyspace creation and `DROP` permissions are not required. Keep credentials in environment variables or a secret manager; migration logs never include connection passwords.

## Sample Application

The [cassandra-sample-app](cassandra-sample-app) module contains a mapped table, a UDT, a repository, and a Docker Compose environment using Cassandra 5.

From the repository root:

```bash
./mvnw -DskipTests clean install
docker compose -f cassandra-sample-app/docker-compose.yml up -d
./mvnw -f cassandra-sample-app/pom.xml spring-boot:run
```

The sample starts in `DRY_RUN`. Apply its reviewed plan with:

```bash
CASSANDRA_MIGRATION_MODE=SAFE_UPDATE \
  ./mvnw -f cassandra-sample-app/pom.xml spring-boot:run
```

On Windows, use `mvnw.cmd` and set `CASSANDRA_MIGRATION_MODE` in PowerShell before running the application.

Stop and remove the local Cassandra environment with:

```bash
docker compose -f cassandra-sample-app/docker-compose.yml down -v
```

## Building From Source

Requirements:

- JDK 17 or newer
- Docker for integration tests
- The included Maven Wrapper

Run the baseline build:

```bash
./mvnw clean verify
```

Run Cassandra integration tests against a selected image:

```bash
./mvnw -Pintegration-tests \
  -Dcassandra.test.image=cassandra:5.0.8 \
  verify
```

Verify the installed starter as an independent Spring Boot consumer:

```bash
./mvnw clean install
./mvnw -f compatibility-tests/pom.xml \
  -Dspring-boot.version=4.1.0 \
  clean verify
```

Use `.\mvnw.cmd` instead of `./mvnw` on Windows. See [CONTRIBUTING.md](CONTRIBUTING.md) for development rules and [docs/publishing.md](docs/publishing.md) for the publishing process.

## Support and Security

Use [GitHub Issues](https://github.com/nguyenductrongdev/spring-schema-auto-migration/issues) for reproducible bug reports and feature proposals.

Report vulnerabilities through GitHub private vulnerability reporting as described in [SECURITY.md](SECURITY.md). Do not disclose an unpatched vulnerability in a public issue.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Changes must preserve the additive-only safety boundary and include focused tests for affected behavior.

## License

Spring Schema Auto Migration is available under the [MIT License](LICENSE).
