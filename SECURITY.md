# Security Policy

## Supported versions

Until `1.0.0`, security fixes are provided only on the latest published `0.x` release or SNAPSHOT line. Older prerelease versions may require an upgrade rather than a backport.

## Reporting a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/nguyenductrongdev/spring-schema-auto-migration/security/advisories/new). Do not open a public issue before a fix and disclosure plan are ready.

Include the affected library version, Spring Boot and Java versions, provider mode, impact, reproduction steps, and a minimal sanitized example. Remove credentials, tokens, certificates, private hostnames, customer data, and production schema details.

The maintainer will acknowledge the report, assess severity and affected versions, coordinate a fix, and credit the reporter when requested and appropriate.

## Credential handling

The library uses Spring Boot's configured Cassandra session by default and does not log connection passwords. Keep Cassandra credentials in environment variables or a secret manager, grant only the required permissions, and use a dedicated `CassandraMigrationSession` when schema DDL privileges must be isolated from application DML privileges.