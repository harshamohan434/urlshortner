# Architecture Overview

## Components

```
Client
  │
  ├── POST   /api/v1/links              → LinkController → LinkService ──┐
  ├── DELETE /api/v1/links/{code}       → LinkController → LinkService   │
  ├── GET    /{code}                    → RedirectController ────────────┤
  ├── GET    /api/v1/links/{code}/stats(/daily) → AnalyticsController    │
  │                                        → AnalyticsService            │
  │                                                                      ▼
  │                                                          ShortLinkRepository ──┐
  │                                                          ClickEventRepository  │
  │                                                                                ▼
  │                                                                     H2 (default) / Postgres (prod)
  │
  └── (write traffic only) RateLimitInterceptor → RateLimiterService (in-memory, per-IP)

Caffeine Cache (in-process) ← read/write via LinkService.resolve / .deactivate
```

**Layering** (enforced by convention, not a framework): `controller` (HTTP/DTO binding only) →
`service` (business rules, transactions) → `repository` (Spring Data JPA) → `domain` entities.
Controllers never talk to repositories directly.

**Packages**, by feature rather than by technical layer:
- `link/` — create/redirect APIs, `ShortLink` entity, `Base62Encoder`, the Caffeine cache config
- `analytics/` — async click capture, `ClickEvent` entity, both stats endpoints
- `ratelimit/` — token-bucket rate limiter, scoped to the create endpoint only
- `config/` — typed `@ConfigurationProperties`, the shared `Clock` bean, MVC interceptor wiring
- `common/` — the single error envelope (`ErrorResponse`) and the exception hierarchy every
  controller's failures map through via one `@RestControllerAdvice`

## Tools and execution approach

- **Stack:** Java 21, Spring Boot 4.1.0 (the current stable line — 3.x is no longer served by
  start.spring.io as of this environment), Maven via the wrapper (no separate Maven install
  needed), H2 (local/default) / PostgreSQL (`prod` profile), Caffeine (in-process cache), JUnit
  5 + Mockito + AssertJ + MockMvc for tests.
- **AI-assisted execution model:** a fixed pipeline defined in `CLAUDE.md` and
  `.claude/agents/*.md` — `requirement-analyst` (normalize intent, surface ambiguity, produce a
  task breakdown) → `codebase-reasoner` (brownfield only: impact analysis before touching
  existing code) → implementation → `test-engineer` equivalent coverage → a quality-gate pass
  (build/test/manual smoke test). Every non-trivial task went through requirement analysis
  before code was written; brownfield work went through impact analysis before code was
  changed. `docs/ai-log.md` is the running, per-task record of what was generated, what was
  edited and why, and what was rejected — that's the traceability artifact, not this document.
- Where the custom subagent types weren't registered mid-session (a harness limitation, not a
  process gap), the same role definitions were run via a general-purpose agent pointed at the
  `.claude/agents/*.md` file directly — same discipline, same output contract, different
  invocation mechanism.

## Control flow: the two hot paths

**Create (`POST /api/v1/links`):** rate-limit check (write traffic only) → validate the
long URL's scheme/length → either verify a custom alias is free, or run a **two-phase insert**
(persist with `code = null` to obtain the auto-increment id, Base62-encode that id, then update
the row with the generated code) → return the link plus a one-time `managementToken`.

**Redirect (`GET /{code}`):** cache-aside lookup (Caffeine first, DB on miss; **misses are
never cached**, so a link created immediately after a 404 is visible on the very next request)
→ check expiry and deactivation against the current time (checked on *every* hit, even a cache
hit, since the cached entity's `expiresAt`/`deactivatedAt` don't change but "now" does) → fire
async analytics recording **without waiting on it** → return `302`. The redirect response is
never blocked on a database write.

## Key decisions (see `docs/ai-log.md` for the full reasoning behind each)

| Decision | Alternative considered | Why this one |
|---|---|---|
| Base62-encode a sequential DB id for auto-generated codes | Random code + collision retry | No retry storms under load; a sequential id can never collide with another auto-generated code |
| Manual Caffeine `Cache` bean | Spring's `@Cacheable` abstraction | Needed control the annotation doesn't give: never cache misses, always recheck expiry/deactivation on a hit |
| Async analytics write, fire-and-forget | Synchronous write on the redirect path | Redirect latency must never depend on a DB write; failures are logged and swallowed, not surfaced to the user |
| One shared error envelope + `@RestControllerAdvice` | Per-endpoint error handling | Every failure mode (400/403/404/409/410/429/500) returns the same `{error, message, details}` shape |
| Rate limiter as a `HandlerInterceptor`, not a servlet `Filter` | Servlet `Filter` | A `Filter`'s exceptions bypass Spring MVC's exception handling entirely; an interceptor's don't, so `429`s go through the same error envelope as everything else |
| Management-token ownership model (ambiguous scenario) | Anyone-with-the-code can delete / full auth system | No auth exists and the code is public by design — a returned-once secret is the minimal correct proof of ownership without building infrastructure the ask didn't justify |
| Java-side day-bucketing for the analytics rollup (brownfield scenario) | SQL `date_trunc` | This project runs on both H2 and Postgres with no precedent for a portable date-trunc query |

## What's deliberately not here

No auth/authorization system, no distributed rate limiting or cache (single-instance,
in-memory), no versioned schema migrations (Hibernate `ddl-auto: update`). All three are
documented, reasoned trade-offs in `docs/testing-and-tradeoffs.md`, not gaps discovered later.
