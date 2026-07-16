# Releasing with JitPack

## Coordinates

JitPack exposes the library modules under the repository-scoped group:

```text
com.github.nguyenductrongdev.spring-schema-auto-migration:schema-auto-migration-spring-boot
com.github.nguyenductrongdev.spring-schema-auto-migration:schema-auto-migration-cassandra-spring-boot-starter
com.github.nguyenductrongdev.spring-schema-auto-migration:schema-auto-migration-bom
```

The version is a Git tag, commit, or branch snapshot requested from JitPack. The `jitpack.yml` build installs only the publishable modules; `cassandra-sample-app` and the standalone `compatibility-tests` project are not exposed as library artifacts.

## Development builds

Use `master-SNAPSHOT` to evaluate the latest code on the default branch. JitPack builds it on demand, so it is mutable and must not be pinned in a production application.

No deploy workflow, Maven server credentials, or package token is required for this public repository.

## Release a version

1. Confirm the full CI matrix passes on the release commit.
2. Create an annotated semantic-version tag.
3. Push the tag to GitHub.
4. Look up the tag on JitPack and confirm every published module builds successfully.
5. Create the matching GitHub release and update the README dependency version.

```bash
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

Consumers then use `v0.1.0` as the Maven dependency version. Never move or reuse a release tag; publish a new patch version for corrections.

Maven Central remains the intended long-term release channel for stable versions that require canonical `io.github.nguyenductrongdev` coordinates and signed artifacts.
