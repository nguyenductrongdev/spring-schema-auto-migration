# Cassandra Auto Migration

Safe additive Cassandra schema evolution for Spring Boot.

Cassandra Auto Migration compares Spring Data Cassandra mappings with the live keyspace at application startup. It can create missing tables and UDTs, and add missing table columns and UDT fields. It never drops, renames, recreates, changes types or keys, or moves data.

> Create new objects and add new fields only. Never update or delete existing schema objects.

The project targets Java 21, Spring Boot 3.x, Spring Data Cassandra, Cassandra Java Driver 4.x, and Cassandra Server 4.1+.

## Status

The current development version is `0.1.0-SNAPSHOT`. It is published to GitHub Packages by pushes to `main` and `develop`.

The API is pre-release and may change before `1.0.0`.

## Supported operations

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
        <url>https://maven.pkg.github.com/nguyenductrongdev/spring-cassandra-auto-migration</url>
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
    <dependency>
        <groupId>io.github.nguyenductrongdev</groupId>
        <artifactId>cassandra-auto-migration-spring-boot-starter</artifactId>
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

## Enable migration

Add the annotation to the Spring Boot application:

```java
import io.github.nguyenductrongdev.cassandra.migration.EnableCassandraAutoMigration;
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
    mode: UPDATE
    fail-on-unsupported: true
    report-unmanaged-objects: true
```

The keyspace must already exist. Keyspace creation and replication changes are deliberately outside this library's scope.

## Execution modes

| Mode | Behavior |
| --- | --- |
| `NONE` | Skips scanning and comparison |
| `VALIDATE` | Compares and reports counts/differences; writes nothing |
| `SCRIPT` | Logs executable additive CQL; writes nothing |
| `UPDATE` | Executes only the generated additive CQL |

`UPDATE` is the default. For a first rollout, start with `SCRIPT`, inspect the generated CQL, and then enable `UPDATE`.

When `fail-on-unsupported=true`, any unsupported difference fails startup before the first additive statement is executed. When false, unsupported differences are logged and safe additive statements may still run.

Set `report-unmanaged-objects=false` when the keyspace intentionally contains tables or UDTs owned by another application.

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

Common scalar types, enums, arrays, `Optional`, `List`, `Set`, `Map`, and mapped UDTs are converted to CQL types. Unknown Java types fail explicitly rather than guessing a Cassandra type.

Applications with custom Cassandra converters can provide their own `JavaTypeResolver` bean. The starter's beans use `@ConditionalOnMissingBean` so focused overrides remain possible.

## How startup works

1. Spring Boot creates `CqlSession` and `CassandraMappingContext`.
2. The starter scans mapped tables, primary keys, columns, UDTs, and fields.
3. Driver metadata is read from the configured keyspace.
4. The comparator creates an in-memory migration plan.
5. Unsupported differences are logged and optionally fail startup.
6. Depending on the mode, additive CQL is logged or executed in dependency order.

Missing UDTs are created before tables that can reference them. Existing UDT fields are added before table columns and new tables are processed. Cassandra schema statements are executed sequentially.

## Sample application

The [sample-app](sample-app) module contains a `Customer` table, an `Address` UDT, a repository, and Docker Compose setup.

On macOS/Linux:

```bash
cd sample-app
docker compose up -d
../mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd sample-app
docker compose up -d
..\mvnw.cmd spring-boot:run
```

The sample defaults to `SCRIPT`. To apply the displayed schema:

```powershell
$env:CASSANDRA_MIGRATION_MODE = "UPDATE"
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

The integration test starts Cassandra 4.1, creates an empty keyspace, applies create and alter plans, and verifies that repeated comparisons are empty.

## SNAPSHOT publishing

`.github/workflows/publish-snapshot.yml` publishes `0.1.0-SNAPSHOT` to GitHub Packages after every push to `main` or `develop`. Maven stores each deployment as a timestamped SNAPSHOT while consumers keep the stable dependency string `0.1.0-SNAPSHOT`.

The workflow uses the repository's `GITHUB_TOKEN` with `packages: write`; no personal token is required for CI publishing. See [docs/publishing.md](docs/publishing.md) for local publishing and consumer details.

## Maven Central

The POM already contains project URL, license, SCM, sources, and Javadocs. Publishing to Maven Central is a future step and will additionally require a verified namespace, signing, Central Portal credentials, and a release profile. Consumers should use GitHub Packages until that release process is added.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please keep changes inside the additive-only safety boundary.

## License

Licensed under the [MIT License](LICENSE).
