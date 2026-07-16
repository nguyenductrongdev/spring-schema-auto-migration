# Publishing

## Coordinates

```text
io.github.nguyenductrongdev:schema-auto-migration-spring-boot-starter:0.1.0-SNAPSHOT
```

The parent POM and starter are deployable. The `cassandra-sample-app` module sets `maven.deploy.skip=true`.

## Automated SNAPSHOT publishing

The `Publish SNAPSHOT` workflow runs on pushes to `master`, `main`, and `develop`.

It:

1. Checks out the repository.
2. Installs JDK 21 and configures Maven server id `github`.
3. Runs the unit tests and package lifecycle.
4. Deploys the parent POM, source JAR, Javadoc JAR, and starter JAR.
5. Authenticates with the workflow `GITHUB_TOKEN`.

The workflow has `contents: read` and `packages: write` permissions.

GitHub Packages stores timestamped files behind the Maven version `0.1.0-SNAPSHOT`. Consumers do not change their dependency version for each commit.

## Publish locally

Create `~/.m2/settings.xml` from [settings.xml](settings.xml). Set a token with `write:packages` and export it:

```bash
export GITHUB_TOKEN=github_pat_xxx
./mvnw clean deploy
```

PowerShell:

```powershell
$env:GITHUB_TOKEN = "github_pat_xxx"
.\mvnw.cmd clean deploy
```

The token and username are never stored in the repository.

## Consume the latest SNAPSHOT

Consumers need the GitHub repository in their POM, a `github` server in Maven settings, and a token with `read:packages`.

Run:

```bash
mvn -U clean verify
```

`-U` forces Maven to refresh remote metadata and resolve the newest timestamped build for `0.1.0-SNAPSHOT`.

## Release versions

Do not overwrite a non-SNAPSHOT version. A future release workflow should:

1. Change the version to a stable semantic version.
2. Run all unit and integration tests.
3. Sign artifacts.
4. Publish immutable artifacts.
5. Create a Git tag and GitHub release.

Maven Central support will require Central Portal namespace verification, credentials, and artifact signing.
