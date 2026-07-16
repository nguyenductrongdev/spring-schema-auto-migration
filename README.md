# Spring Schema Auto Migration

Extensible, safe additive schema evolution for Spring Boot.

Spring Schema Auto Migration currently provides a Cassandra migration provider behind a database-neutral library name. It compares Spring Data mappings with the live schema during application startup and applies only explicitly supported additive changes.

> Create new objects and add new fields only. Never update or delete existing schema objects.

The project requires Java 21. The Cassandra provider targets Spring Boot 3.5.x, Spring Data Cassandra, Cassandra Java Driver 4.x, and Cassandra Server 4.1+.

## Status

The current development version is `0.1.0-SNAPSHOT`. It is published to GitHub Packages by pushes to `master`, `main`, and `develop`.

The API is pre-release and may change before `1.0.0`.

## Compatibility

The main branch follows the Spring Data Cassandra and Cassandra Java Driver versions managed by Spring Boot 3.5.x. The integration suite verifies additive migrations against Cassandra 4.1.

## Provider model

The Maven artifact and top-level namespace are database-neutral. Provider implementations remain explicit:

- Cassandra provider: `io.github.nguyenductrongdev.automigration.cassandra`
- Future providers can live under sibling namespaces such as `.jdbc`
- Provider-specific entry points and settings keep their database name, such as `@EnableCassandraAutoMigration` and `cassandra.auto-migration.*`

This keeps consumers from confusing Cassandra behavior with future providers while allowing shared migration abstractions to move into `io.github.nguyenductrongdev.automigration` later.

The Spring Boot auto-configuration and Spring Data Cassandra dependencies are optional. Applications add the normal Spring Boot Cassandra starter, so consuming the library does not activate Cassandra unless the application enables the provider.

## Cassandra supported operations

| Difference | Automatic action |
| --- | --- |
| Missing UDT | `CREATE TYPE IF NOT EXISTS` |
| Missing UDT field | `ALTER TYPE ... ADD IF NOT EXISTS` |
| Missing table | `CREATE TABLE IF NOT EXISTS` |
| Missing non-key table column | `ALTER TABLE ... ADD IF NOT EXISTS` |

All generated operations are additive. A second run against the migrated schema produces no statements.

## Never automated

The library reports but never executes:

- Dropping tables, columns, UDTs, or UDT fields
- Renaming tables, columns, UDTs, or UDT fields
- Changing column or UDT field types
- Changing partition, clustering, or primary keys
- Changing clustering column order (`ASC`/`DESC`)
- Recreating tables or UDTs
- Data migration or backfill
- Index, materialized view, table option, and replication changes

The comparison is stateless. A rename cannot be proven from two schema snapshots, so it normally appears as a new mapped object plus an old unmanaged object. No rename or data copy is attempted.

## Install from GitHub Packages

Add the GitHub Packages repository and dependency:

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
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <!-- Add the normal Spring Data Cassandra starter. -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-cassandra</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.nguyenductrongdev</groupId>
        <artifactId>schema-auto-migration-spring-boot-starter</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

GitHub Packages Maven downloads require credentials. Add a server with the same `github` id to `~/.m2/settings.xml` (or `%USERPROFILE%\.m2\settings.xml` on Windows):

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
```

Use a GitHub token with `read:packages` in `GITHUB_TOKEN`. A complete example is in [docs/settings.xml](docs/settings.xml).

Maven caches SNAPSHOT metadata. Force an immediate check for the newest timestamped artifact with:

```bash
mvn -U clean verify
```

The `-U` flag forces Maven to check remote repositories for updated releases and SNAPSHOTs instead of waiting for the normal update interval.

## Enable Cassandra migration

Add the annotation to the Spring Boot application:

```java
import io.github.nguyenductrongdev.automigration.cassandra.EnableCassandraAutoMigration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCassandraAutoMigration
public class Application {
}
```

Configure the normal Spring Cassandra connection and migration mode:

```yaml
spring:
  cassandra:
    contact-points: localhost
    port: 9042
    local-datacenter: datacenter1
    keyspace-name: application_keyspace
    schema-action: none

cassandra:
  auto-migration:
    mode: DRY_RUN
```

The keyspace must already exist. Keyspace creation and replication changes are deliberately outside this library's scope. In `SAFE_UPDATE`, a missing keyspace fails application startup before entity scanning, comparison, or CQL execution.

## Execution modes

| Mode | Behavior |
| --- | --- |
| `NONE` | Default. Skips schema scanning and comparison |
| `DRY_RUN` | Compares schemas and logs the ordered migration plan without executing it |
| `SAFE_UPDATE` | Rejects unsupported differences, then executes only provider-supported additive operations |

`DRY_RUN` logs the complete ordered plan through the provider's dedicated plan logger. It never creates a report file or executes a migration.

Applications can route this logger to a separate file or centralized logging system through their normal Logback or Log4j configuration.

`NONE` is the default. For a first rollout, enable `DRY_RUN`, inspect the logged plan, and then enable `SAFE_UPDATE`.

Any unsupported difference always fails startup before the first additive statement is executed. In `DRY_RUN`, the complete plan is logged before startup fails.

For Cassandra, existing tables and UDTs without a mapped application type are always reported as unsupported differences. Use a dedicated keyspace when other applications own schema objects that are not part of this application's mappings.

If `cassandra.auto-migration.keyspace-name` is omitted, the starter uses the keyspace from the active `CqlSession`.

## Mapping support

The scanner reads Spring Data Cassandra types registered in `CassandraMappingContext`:

- `@Table`
- `@UserDefinedType`
- `@PrimaryKey`
- `@PrimaryKeyClass` and `@PrimaryKeyColumn`, including clustering `ASC`/`DESC` ordering
- `@Column`, including static columns
- `@CassandraType`
- `@Frozen`
- `@VectorType` when the Cassandra server supports vectors
- `@Transient`

Common scalar types, enums, arrays, `Optional`, `List`, `Set`, `Map`, and mapped UDTs are converted to CQL types through Spring Data Cassandra's public mapping metadata. Unknown Java types fail explicitly rather than guessing a Cassandra type.

## How startup works

1. Spring finishes creating all non-lazy singleton beans, including standard Spring Boot data initialization.
2. Enabled providers scan their Spring Data mapping contexts, inspect their databases, and build plans in parallel using Java 21 virtual threads.
3. The coordinator waits for every provider and validates all completed plans in deterministic provider order.
4. If any planning or validation fails, no provider starts execution.
5. Valid `SAFE_UPDATE` providers execute in parallel. Operations inside each provider remain sequential to preserve dependencies.
6. The coordinator waits for every execution before Spring starts lifecycle components such as servlet and reactive web servers.

A migration failure aborts application-context refresh before the web server starts accepting requests.

Missing UDTs are created before tables that can reference them. Existing UDT fields are added before table columns and new tables are processed.

Execution cannot be atomic across different databases. A runtime failure in one provider cannot roll back changes already acknowledged by another provider, but global validation prevents known unsupported plans from causing this kind of partial execution.

## Sample applications

The [cassandra-sample-app](cassandra-sample-app) module contains a `Customer` table, an `Address` UDT, a repository, and Docker Compose setup.

On macOS/Linux:

```bash
cd cassandra-sample-app
docker compose up -d
../mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd cassandra-sample-app
docker compose up -d
..\mvnw.cmd spring-boot:run
```

The sample defaults to `DRY_RUN`. To apply the logged schema:

```powershell
$env:CASSANDRA_MIGRATION_MODE = "SAFE_UPDATE"
..\mvnw.cmd spring-boot:run
```

## Build and test

Use the included Maven Wrapper:

```bash
./mvnw clean verify
```

Run the Testcontainers integration test when Docker is available:

```bash
./mvnw -Pintegration-tests verify
```

The integration profile runs the Cassandra 4.1 Testcontainers suite. It applies create and additive update plans and verifies that repeated comparisons are empty.

## SNAPSHOT publishing

`.github/workflows/publish-snapshot.yml` publishes `0.1.0-SNAPSHOT` to GitHub Packages after every push to `master`, `main`, or `develop`. Maven stores each deployment as a timestamped SNAPSHOT while consumers keep the stable dependency string `0.1.0-SNAPSHOT`.

The workflow uses the repository's `GITHUB_TOKEN` with `packages: write`; no personal token is required for CI publishing. See [docs/publishing.md](docs/publishing.md) for local publishing and consumer details.

## Maven Central

The POM already contains project URL, license, SCM, sources, and Javadocs. Publishing to Maven Central is a future step and will additionally require a verified namespace, signing, Central Portal credentials, and a release profile. Consumers should use GitHub Packages until that release process is added.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please keep changes inside the additive-only safety boundary.

## License

Licensed under the [MIT License](LICENSE).
