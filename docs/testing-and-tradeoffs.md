# Testing Approach, Limitations & Trade-offs

## Testing approach

- **Unit tests** for logic with no Spring/DB dependency: `Base62EncoderTest` (padding,
  round-trip, overflow-without-truncation, invalid input), `TokenBucketTest` (capacity, refill,
  cap-at-capacity, retry-after) — the latter uses a manually-advanceable `Clock` test double
  instead of real `Thread.sleep`s, so refill behavior is deterministic and fast.
- **Integration tests** (`UrlShortenerFlowIntegrationTest`, `@SpringBootTest` + MockMvc against
  H2): the full create → redirect → async-analytics → expiry → rate-limit flow, exercised
  through real HTTP request/response cycles rather than testing layers in isolation. Async
  analytics assertions poll the stats endpoint with a timeout rather than assuming immediate
  consistency, since the write genuinely happens off the request thread.
- **Config smoke test** (`ProdProfileConfigTest`): confirms the `prod` profile's Postgres
  properties bind correctly, without starting a real `DataSource` — so it passes with no live
  Postgres instance, but still catches YAML/profile-activation mistakes.
- **Manual smoke test** against the actually-running service via `curl` (not just MockMvc) —
  see `docs/ai-log.md` Entry 2 for the exact commands and observed responses.

Test-isolation note: `@SpringBootTest` caches the application context across test methods with
identical configuration, so the `RateLimiterService` singleton (and its per-IP bucket state)
persists across the 6 methods in `UrlShortenerFlowIntegrationTest`. A `reset()` method is
called in `@BeforeEach` to prevent one test's requests from exhausting another's rate-limit
budget — this was caught by running the suite, not anticipated up front (see `docs/ai-log.md`).

## Known limitations (shipped deliberately, not oversights)

| Limitation | Why it's acceptable for this prototype | What v2 would do |
|---|---|---|
| `hibernate.ddl-auto: update` in both default and `prod` profiles, no Flyway | No time budget today for migration authoring; `update` is safe for a from-scratch schema | Introduce Flyway with versioned migrations before any real production data exists |
| Rate limiting is in-memory, per-instance, keyed by raw `getRemoteAddr()` | No distributed store dependency needed; correct for a single-instance prototype | Move to a shared store (e.g. Redis) for multi-instance deployments; add a trusted-proxy header policy |
| No authentication | Out of scope per the original requirement framing (anonymous create) | Add auth + per-user link ownership, quotas beyond IP-based rate limiting |
| `UserAgentClassifier` is a coarse substring heuristic | Requirement asked for "coarse" device/browser data; a real UA database is a new dependency not justified for that bar | Swap in a proper UA-parsing library if richer analytics are needed |
| Generated Base62 codes and custom aliases share one namespace | Simplicity; documented, low-probability collision path fails loudly instead of corrupting data (`CodeGenerationConflictException`, 500) | Reserve a disjoint character range/prefix for custom aliases so the two can never collide |
| No SSRF/reputation defense on submitted long URLs beyond scheme allow-listing | Full SSRF protection (resolving and validating target hosts) is a substantial feature on its own | Add destination validation/allow-listing if this service is ever given outbound network reach implications |
| Caffeine cache is per-instance, not shared | No Redis dependency for v1; acceptable staleness/cold-start behavior for a prototype | Move to a shared cache if deployed with multiple instances behind a load balancer |

## Trade-offs made under the compressed timeline

- Implemented directly rather than round-tripping every task through a separate agent spawn —
  faster, while still following the same task-spec/acceptance-criteria discipline and logging
  to `docs/ai-log.md` (see `CLAUDE.md` for the intended pipeline).
- Environment-driven stack decision: targeted Spring Boot 4.1.0 / Java 21 (the current stable
  line) rather than an older, better-known version, because the environment's toolchain and
  `start.spring.io` itself no longer support anything older — documented in `docs/ai-log.md`
  Entry 1 rather than silently worked around.
