# URL Shortener

A URL shortener service — core APIs, click analytics, and reliability features (rate limiting,
async analytics, cache-aside redirects, link expiry) — built with disciplined, AI-assisted
engineering execution. See `CLAUDE.md` for the process/pipeline this project follows, and
`docs/` for architecture, scenarios, and testing/trade-off notes.

## Stack

Java 21, Spring Boot 4.1.0, Maven (via wrapper), H2 (default profile) / PostgreSQL (`prod`
profile), Caffeine (in-process cache).

## Prerequisites

- **JDK 21+**. The Maven wrapper (`mvnw`) does not require Maven to be installed separately.
- If your system's default `java` is older than 17, point `JAVA_HOME` at a JDK 21 install for
  the commands below (adjust the path to wherever your JDK 21 actually lives):

  **PowerShell:**
  ```powershell
  $env:JAVA_HOME = "C:\path\to\jdk-21"
  $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
  java -version   # should print 21.x
  ```

  **bash:**
  ```bash
  export JAVA_HOME="/path/to/jdk-21"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

## Run locally (H2, zero setup)

```powershell
.\mvnw.cmd spring-boot:run
```
```bash
./mvnw spring-boot:run
```

Starts on `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`.
H2 console (inspect the in-memory DB): `http://localhost:8080/h2-console` — JDBC URL
`jdbc:h2:mem:urlshortener`, user `sa`, empty password.

## Run against Postgres instead

```bash
export DB_URL="jdbc:postgresql://localhost:5432/urlshortener"
export DB_USERNAME="urlshortener"
export DB_PASSWORD="yourpassword"
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Run tests

```bash
./mvnw test
```

## API quickstart

**Create a short link:**
```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.example.com/some/long/path"}'
# -> 201 {"code":"0000001","shortUrl":"http://localhost:8080/0000001","longUrl":"...",
#         "expiresAt":null,"createdAt":"...","managementToken":"c4657abf-..."}
```
Save the `managementToken` — it's shown **only here, once**. It's the only proof of ownership
this no-auth service has, and it's required to delete the link later (see below).

With a custom alias and an expiry:
```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.example.com","customAlias":"my-link","expiresAt":"2027-01-01T00:00:00Z"}'
```

**Follow the short link** (redirects with `302`):
```bash
curl -i http://localhost:8080/0000001
```

**Read click analytics:**
```bash
curl http://localhost:8080/api/v1/links/0000001/stats
# -> {"code":"0000001","clickCount":2,"lastAccessedAt":"...","createdAt":"...","expiresAt":null,
#     "recentReferrers":["direct"],"deviceBreakdown":{"desktop":2}}
```

**Daily click rollup** (last N days, default 7, range 1-90 — for charting trends):
```bash
curl "http://localhost:8080/api/v1/links/0000001/stats/daily?days=3"
# -> {"code":"0000001","days":[{"date":"2026-08-18","count":0},{"date":"2026-08-19","count":2},{"date":"2026-08-20","count":0}]}
```

**Take a link back down** (requires the `managementToken` from creation):
```bash
curl -X DELETE http://localhost:8080/api/v1/links/0000001 -H "X-Management-Token: c4657abf-..."
# -> 204. The link is deactivated (not deleted — history/analytics are kept) and the redirect
#    immediately starts returning 410 link_deactivated, not a stale cached 302.
```

### Error responses

Every error follows one envelope: `{"error": "<slug>", "message": "...", "details": {...}}`
(`details` omitted when empty).

| Status | `error` | When |
|---|---|---|
| 400 | `invalid_request` | Malformed body, invalid URL scheme, invalid custom alias, `days` out of 1-90 range |
| 403 | `access_denied` | Missing or incorrect `X-Management-Token` on delete |
| 404 | `not_found` | Unknown short code |
| 409 | `alias_conflict` | Custom alias already taken |
| 410 | `link_expired` / `link_deactivated` | Link's `expiresAt` has passed / owner took it down |
| 429 | `rate_limit_exceeded` | Too many `POST /api/v1/links` calls from one client (see `Retry-After` header) |
| 500 | `internal_error` / `code_generation_conflict` | Unexpected server error / rare code-namespace collision (see `docs/ai-log.md`) |

## Configuration

See `src/main/resources/application.yml` — `urlshortener.base-url`, `urlshortener.code-length`,
`urlshortener.rate-limit.capacity` / `refill-per-minute`.

## Project layout

```
src/main/java/com/urlshortener/
  link/        create-link + redirect APIs, ShortLink entity, Base62 encoder, cache
  analytics/   async click capture, stats API, ClickEvent entity
  ratelimit/   token-bucket rate limiter (create endpoint only)
  config/      typed properties, Clock bean, MVC interceptor wiring
  common/      shared error envelope + exception hierarchy
```
