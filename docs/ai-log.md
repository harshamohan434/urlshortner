# AI-Assisted Execution Log

Running record of AI-generated/edited/rejected work with rationale. One entry per meaningful
unit of work, newest last.

---

## Entry 1 — Project scaffold

**Task:** Scaffold a Spring Boot project skeleton before any feature work.

**Generated:** Maven project via start.spring.io (Java 21, Spring Boot 4.1.0 — the current
stable line; 3.x is no longer offered by start.spring.io), Maven wrapper, base `pom.xml`,
`application.yml` with H2 default / Postgres `prod` profiles.

**Edited:**
- start.spring.io's form accepted `bootVersion=4.0.7.RELEASE` but generated an invalid
  parent-POM version (Spring Boot dropped the `.RELEASE` suffix in recent releases); corrected
  to `4.1.0` by checking Maven Central's actual artifact metadata directly rather than trusting
  the form response.
- Added `com.github.ben-manes.caffeine:caffeine` explicitly — `spring-boot-starter-cache`
  alone only provides the caching *abstraction*, not a cache provider.
- Converted the generated `application.properties` to `application.yml` with explicit
  `default`/`prod` profile documents for readability.

**Rejected:** Initially targeted Spring Boot 2.7.18 / Java 11 to match the only pre-installed
JDK, to avoid an install under time pressure. Rejected once start.spring.io confirmed 3.x is
no longer served at all (Java 17 is the new minimum) — shipping a multi-years-EOL framework
version isn't defensible as "production-grade" even under a deadline. Used an already-present
JDK 21 (`~/.jdks/ms-21.0.7`, IntelliJ-managed) instead of installing a new one.

**Validated:** `./mvnw compile` and `./mvnw test` both green against the bare scaffold before
any feature code was added.

---

## Entry 2 — Greenfield v1 (create link, redirect, analytics, expiry, rate limiting)

**Task:** Implement the 8-task breakdown produced by requirement analysis (see chat transcript
/ PR description for the full ambiguity table and task list): domain model, Base62 encoder,
create-link API, redirect API + cache, async analytics capture + query API, expiry (410)
handling, rate limiting, global error handling, and the test suite.

**Generated:** All production code under `src/main/java/com/urlshortener/{link,analytics,
ratelimit,config,common}` and the accompanying test suite, written directly (not via a second
round-trip through a code-generation pass) following the task spec's fixed constraints and
acceptance criteria.

**Edited / caught during validation (not just "generated and shipped"):**
1. **Real bug, not a style nit:** `RateLimiterService` originally had two constructors (a
   public one and a package-private test-only one) with neither marked `@Autowired`. Spring
   couldn't resolve which to use, which broke the *entire* application context at startup —
   caught by actually running `./mvnw test`, not just `compile`. Fixed by introducing a proper
   `Clock` bean (`ClockConfig`) and reducing `RateLimiterService` to a single constructor.
   This is exactly why "compiles" was never treated as "done" in this build.
2. **Test-isolation bug:** the first integration test run showed 4/6 tests failing with 429s
   they shouldn't have received. Root cause: Spring caches the application context (and the
   `RateLimiterService` singleton) across test methods in a class, and MockMvc's default
   remote address is always `127.0.0.1` — so earlier tests' `POST` calls were silently
   exhausting the rate-limit bucket before later tests ran. Fixed with an explicit
   `RateLimiterService.reset()` test-support method called in `@BeforeEach`, rather than
   weakening the assertions to accommodate the shared state.
3. Jackson/test-autoconfigure package locations in this Spring Boot 4.1 / Jackson 3
   environment differ from what's documented for 3.x (`ObjectMapper` now lives under
   `tools.jackson.databind`, `AutoConfigureMockMvc` moved to
   `org.springframework.boot.webmvc.test.autoconfigure`, `ConfigDataApplicationContextInitializer`
   is under `org.springframework.boot.test.context`). Resolved by inspecting the actual jars in
   the local Maven repo rather than guessing from memory of older Boot versions — memory of
   framework internals is exactly the kind of thing worth verifying against the real
   environment instead of trusting.

**Rejected:** Considered a Spring `@Cacheable`-annotation approach for the redirect cache
instead of a manual Caffeine `Cache` bean. Rejected because the requirement (never cache
misses, and re-check expiry on every hit against a value that doesn't itself change) needed
finer control over cache-population and read semantics than the annotation abstraction
naturally gives without extra plumbing.

**Known, documented limitations shipped deliberately (not oversights):**
- `ddl-auto: update` used for both default and `prod` profiles — no Flyway/versioned
  migrations in this prototype (see `docs/testing-and-tradeoffs.md`).
- Rate limiting is in-memory per instance, keyed by raw `request.getRemoteAddr()` — not
  distributed, and not proxy-aware (no `X-Forwarded-For` trust configured).
- `UserAgentClassifier` is a coarse substring heuristic, not a real UA database — deliberate
  scope trade-off to avoid pulling in a new dependency for a "coarse" analytics field.
- Generated Base62 codes and custom aliases share one code namespace; a rare, documented edge
  case (`CodeGenerationConflictException`) exists if a custom alias happens to collide with a
  future auto-generated code. Fails loudly (500) rather than silently corrupting data; not
  fully solved (would need a disjoint alias/code namespace in v2).

**Validated:**
- `./mvnw test`: 20/20 tests passing (unit: Base62 encoder, token bucket refill/capacity;
  integration: full create→redirect→analytics→expiry→rate-limit flow against H2; a
  property-binding-only smoke test confirming the `prod` profile's Postgres config loads
  without needing a live Postgres instance).
- Manual smoke test against the actually-running service (`./mvnw spring-boot:run` +
  `curl`): create, redirect (302 + correct `Location`), async analytics (click count updated
  correctly), 404, custom alias, 409 conflict, and 400 invalid-scheme rejection all verified
  against real HTTP responses, not just MockMvc.

---

## Entry 3 — Brownfield: daily analytics rollup

**Task:** Add `GET /api/v1/links/{code}/stats/daily?days=N` — click counts bucketed by day —
on top of the already-live v1 service. Routed through a codebase-reasoner-style impact
analysis first (brownfield discipline: trace the change surface before touching working code),
which is where the two real decisions below came from.

**Impact analysis findings acted on (not skipped):**
1. **Missing index risk**: `ClickEvent` only had an index on `code`; a "last N days" query
   filters on `code` + `occurredAt` both. Added a composite `(code, occurredAt)` index
   *additively* (kept the existing single-column index) rather than replacing it, to keep this
   a purely additive brownfield change per the analysis's own "minimal invasive" recommendation.
2. **Dialect portability**: this project runs both H2 (dev) and Postgres (prod) with no
   existing precedent for a native date-trunc query. Chose to fetch raw timestamps and bucket
   by `LocalDate` in Java (same pattern `getStats` already uses for `recentReferrers`) instead
   of a SQL-side `GROUP BY date_trunc(...)`, to avoid a query that behaves differently per
   dialect.

**Generated:** `ClickEventRepository.findOccurredAtByCodeSince`, `AnalyticsService
.getDailyStats`, `DailyStatsResponse` DTO, `AnalyticsController` `/stats/daily` handler,
`InvalidRequestException` (+ handler) for `days` param range validation (1-90).

**Edited:** Also adopted the impact analysis's suggestion to constructor-inject `Clock` into
`AnalyticsService` (it was calling `Instant.now()` directly) — makes day-boundary bucketing
logic deterministic and testable with a fixed clock, and brings it in line with the
`RateLimiterService`/`ClockConfig` pattern already established.

**Validated:** Unit tests (`AnalyticsServiceTest`, fixed `Clock`) covering day-boundary
bucketing and zero-fill for click-free days; integration tests (`AnalyticsControllerIntegrationTest`)
for 404/400/200 shapes. 27/27 tests green. No dedicated analytics test class existed before
this change — the impact analysis flagged that gap explicitly, so these tests are a
prerequisite of the change, not optional extra coverage.

**Sign-off:** Impact analysis rated this low/medium impact — no schema-breaking change, no
hot-path touch, no auth, purely additive endpoint — so no mandatory human sign-off was
required per project policy, and none was sought.

---

## Entry 4 — Ambiguous requirement: "let people take their link back down"

**Task as given (deliberately underspecified):** "Let people take their short link back down
if they change their mind."

**Requirement interpretation (the actual work here, not the code):**

| Ambiguity | Rejected option | Chosen resolution | Why |
|---|---|---|---|
| Who can delete a link? | Anyone who knows the short code | A one-time management token, returned only in the create response, required via `X-Management-Token` header to delete | Knowing the code is public by design (it's the whole point of the link) — that's not proof of ownership. Full auth is real infrastructure this system doesn't have and the ask doesn't justify building it today. |
| Delete vs. deactivate | Hard-delete the row | Soft: set `deactivatedAt`, keep the row + analytics history | Matches how expiry already works — one "gone" semantic, not two different code paths for "TTL passed" vs "actually removed." |
| Token exposure | Return it on every future read | Shown once, at creation, never again | `LinkResponse` (the only place the token appears) is only ever returned by the create endpoint — no separate "leak prevention" logic needed, the existing API shape already enforces it. |

**Generated:** `ShortLink.managementToken`/`deactivatedAt`/`isDeactivated()`/`deactivate()`,
`LinkService.deactivate` (token check via `MessageDigest.isEqual` — constant-time comparison,
not `String.equals`, to avoid a timing side-channel on the token check), `DELETE
/api/v1/links/{code}`, `LinkDeactivatedException` (410, distinct `error` slug from
`link_expired` so a client can tell the two apart), `LinkAccessDeniedException` (403).

**Caught during implementation, not just "generated and shipped":** the redirect hot path
uses a Caffeine cache-aside lookup (`LinkService.resolve`) with no existing invalidation
trigger — deactivating a link without evicting its cache entry would have kept it redirecting
for up to the cache's TTL, directly defeating the feature ("take it down" would silently not
take it down for several minutes). Fixed by having `LinkService.deactivate` call
`cache.invalidate(code)` unconditionally. Verified with a real, non-mocked check: manual
`curl` smoke test hit the redirect endpoint *immediately* after a successful delete and got
`410 link_deactivated` on the very next request, not a stale `302`.

**Rejected:** Considered making delete idempotent-with-error (404 on a second delete of an
already-deactivated link with the same valid token). Rejected in favor of idempotent-success
(repeated `204`) — standard DELETE semantics, and there's no reason a repeat request with a
still-valid token should be treated as a client error.

**Validated:** `LinkDeactivationIntegrationTest` — correct-token success, wrong-token 403,
missing-token 403, unknown-code 404, idempotent repeat-delete — plus a manual `curl` run
against the live service (on a separate port, to avoid touching another already-running
instance) confirming the full flow including the immediate-cache-eviction behavior above.
32/32 tests green.
