# Publishing

## Coordinates

A release deploys these artifacts with one shared version:

```text
io.github.nguyenductrongdev:schema-auto-migration-parent
io.github.nguyenductrongdev:schema-auto-migration-spring-boot
io.github.nguyenductrongdev:schema-auto-migration-cassandra-spring-boot-starter
io.github.nguyenductrongdev:schema-auto-migration-bom
```

The `cassandra-sample-app` module sets `maven.deploy.skip=true`. The standalone `compatibility-tests` project is outside the reactor and is never deployed.

## Automated SNAPSHOT publishing

The `Publish SNAPSHOT` workflow runs on pushes to `master`, `main`, and `develop`. It:

1. Checks out the repository using a commit-pinned action.
2. Installs JDK 17 and configures Maven server id `github`.
3. Runs tests and the deploy lifecycle.
4. Publishes POMs, binaries, source JARs, and Javadoc JARs.
5. Authenticates with the workflow-scoped `GITHUB_TOKEN`.

The workflow has only `contents: read` and `packages: write` permissions. GitHub Packages stores timestamped builds behind `0.1.0-SNAPSHOT`, so consumers keep one dependency version.

## Publish locally

Create `~/.m2/settings.xml` from [settings.xml](settings.xml). Provide a token with `write:packages` through the environment:

```bash
export GITHUB_TOKEN=<token-with-write-packages>
./mvnw clean deploy
```

PowerShell:

```powershell
$env:GITHUB_TOKEN = "<token-with-write-packages>"
.\mvnw.cmd clean deploy
```

Never store the token or Maven settings containing a literal token in this repository.

## Consume the latest SNAPSHOT

Consumers need the GitHub Packages repository in their POM, a matching `github` server in Maven settings, and a token with `read:packages`.

```bash
mvn -U clean verify
```

`-U` refreshes remote metadata and resolves the newest timestamped `0.1.0-SNAPSHOT` build.

## Release versions

Do not overwrite a non-SNAPSHOT version. A stable release workflow must run the full compatibility and integration matrix, sign artifacts, publish immutable coordinates, create a Git tag and GitHub release, and preserve provenance.

Publishing to Maven Central additionally requires Central Portal namespace verification, credentials, artifact signing, and release-specific deployment configuration.