# Three Scenarios: Greenfield, Brownfield, Ambiguous

Each below follows the same shape: decomposition → execution → validation. Full prompt-level
traceability (generated/edited/rejected, with rationale) lives in `docs/ai-log.md` — this
document is the higher-level walkthrough of *why* each scenario counts as what it says it is.

---

## 1. Greenfield — URL shortener v1

**What made it greenfield:** the repository contained only an empty Java module stub — no
domain code, no tests beyond the generated placeholder. Confirmed by inspection before writing
anything, not assumed.

**Decomposition:** the ask — "core APIs, analytics, and reliability features" — was normalized
into a concrete problem statement, an explicit ambiguity table (auth, persistence, ID
strategy, analytics granularity, expiry semantics, rate-limit policy — each resolved with a
stated default and rationale, not silently guessed), and 8 dependency-ordered tasks: domain
model, Base62 encoder, create API, redirect API + cache, async analytics, expiry handling,
rate limiting, error handling, and the test suite.

**Execution:** implemented directly against that task spec (see `docs/ai-log.md` Entry 2 for
what was generated vs. edited vs. rejected). Two real bugs were caught *during* execution, not
after: an ambiguous-constructor dependency-injection bug that broke the entire application
context at startup, and a test-isolation bug where Spring's context caching let one test's
requests silently exhaust another test's rate-limit bucket. Both are documented with root
cause and fix, not just patched silently.

**Validation:** 20/20 automated tests (unit + full HTTP-level integration flow against H2, plus
a property-binding smoke test for the `prod` profile that needs no live Postgres) and a manual
`curl` session against the actually-running service confirming create/redirect/analytics/
errors match the documented contract.

---

## 2. Brownfield — daily analytics rollup

**What made it brownfield:** added to an already-working, already-tested v1 service. The whole
point of this scenario is demonstrating safe change to live code, not building something new.

**Decomposition:** before any code changed, a codebase-reasoner-style impact analysis traced
the actual change surface (which files/classes/endpoints), the current analytics data flow,
and — critically — the *blast radius*: whether this touches the cache (it doesn't), the rate
limiter (it doesn't, confirmed by reading the interceptor's exact path-pattern scope), the
async write path (it doesn't), or introduces new risk (it does: no existing index supports a
date-range query, and no precedent exists in this codebase for a query portable across the
H2/Postgres split this project maintains). Existing test coverage in the area was checked too —
there wasn't any dedicated analytics test class, which meant new tests here were a
prerequisite of the change, not optional polish.

**Execution:** the two real decisions the impact analysis surfaced were made explicitly rather
than left to implementation-time guesswork — add a composite index *additively* (minimal
invasive change), and bucket by day in Java rather than SQL (dialect portability). Along the
way, `AnalyticsService` was also changed to take an injected `Clock` instead of calling
`Instant.now()` directly, matching a pattern already established elsewhere in the codebase —
consistency, not scope creep, and it's what makes the new bucketing logic testable at all.

**Validation:** the change was additive only — nothing in the existing 20-test suite needed to
change, and it stayed green throughout. 7 new tests added (unit-level day-boundary bucketing
with a fixed clock, integration-level request validation and response shape). Impact analysis
rated this low/medium risk with no schema-breaking change, no hot-path touch, and no auth
involvement, so no mandatory human sign-off was required — and that call is recorded, not
just assumed.

---

## 3. Ambiguous — "let people take their link back down"

**What made it ambiguous:** the request as stated doesn't specify *who* is allowed to do this,
and this system has no authentication — so "let the owner delete it" isn't something the code
can check by identity. That gap had to be resolved by judgment, not by asking the requirement
to be more specific than a real one-line stakeholder ask usually is.

**Decomposition (the actual work, before any code):** three ambiguities identified, each with
a rejected alternative and a chosen resolution with stated rationale — captured in full in
`docs/ai-log.md` Entry 4. In short: ownership is proven by a one-time management token (not by
knowing the public short code, and not by building an auth system the ask didn't justify);
"take down" means soft-deactivate, not hard-delete (consistent with how expiry already works);
the token is shown exactly once, enforced by the API's existing shape rather than by extra
logic.

**Execution:** implementing the *interpretation* was almost mechanical once the resolution was
decided — the harder part was in the decomposition above. One real implementation risk
surfaced during execution, though: the redirect path's Caffeine cache had no existing
invalidation trigger, so deactivating a link without evicting its cache entry would have kept
it redirecting for up to the cache's TTL — silently defeating the entire feature. Fixed by
having the deactivation path evict the cache entry unconditionally, and specifically verified
(not assumed) with a real `curl` check: redirect immediately after delete, not "eventually."

**Validation:** 5 new integration tests (correct token succeeds, wrong/missing token is
rejected with no side effect, unknown code 404s, repeated delete is idempotent) plus the manual
cache-eviction check above. 32/32 tests green across the full suite.
