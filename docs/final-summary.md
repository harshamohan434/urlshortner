# Final Engineering Summary

## Plan and rationale

The brief was to build a URL shortener demonstrating AI-assisted, engineer-led execution
across greenfield, brownfield, and ambiguous work. The plan was: scaffold a real toolchain
first (not a mock), build v1 through explicit requirement decomposition rather than jumping
straight to code, then deliberately create — not cherry-pick — a brownfield enhancement and an
ambiguous requirement on top of the stable v1, running each through the same
decompose-before-execute discipline. A fixed subagent pipeline (`requirement-analyst` →
`codebase-reasoner` for brownfield → implementation → validation) was set up in
`.claude/agents/*.md` and `CLAUDE.md` *before* any feature code, specifically so it would be
reusable across all three scenarios rather than invented ad hoc per task.

Execution was compressed into roughly one working day once the deadline moved up, which
shaped several calls below — documented as trade-offs, not hidden as shortcuts.

## Artifacts produced

- **Working prototype**: `src/` — greenfield v1 (create/redirect/analytics/expiry/rate
  limiting), brownfield addition (daily analytics rollup), ambiguous-requirement resolution
  (management-token-gated link deactivation). 32 automated tests, all green.
- **Process**: `CLAUDE.md` + `.claude/agents/` (5 role-scoped subagents mirroring the
  engineering lifecycle: requirement analysis, brownfield impact analysis, implementation,
  test coverage, quality gate).
- **Documentation**: `README.md` (setup + API), `docs/architecture.md` (components, control
  flow, key decisions), `docs/scenarios.md` (the three required scenarios, each with
  decomposition/execution/validation), `docs/testing-and-tradeoffs.md` (testing approach,
  known limitations), `docs/ai-log.md` (per-task traceability: generated/edited/rejected, with
  rationale — the most granular record of AI-assisted execution in this project).

## Risks, trade-offs, and how they were validated

The full, itemized list lives in `docs/testing-and-tradeoffs.md` and `docs/architecture.md`'s
"key decisions" table — summarized here:

- **No authentication.** Deliberate v1 scope boundary, not an oversight — the ambiguous
  scenario's resolution (a per-link management token) was specifically designed to work
  *without* auth, rather than assuming auth would exist.
- **Single-instance, in-memory rate limiting and caching.** No distributed store dependency;
  correct for a single-instance prototype, documented as a scaling limitation for
  multi-instance deployment.
- **No versioned schema migrations** (`ddl-auto: update` instead of Flyway). Acceptable for a
  from-scratch schema with no production data yet; explicitly flagged as the first thing to
  change before any real data exists.
- **Environment-driven stack decision**: targeted Spring Boot 4.1.0 / Java 21 rather than an
  older, more conventionally-expected version, because the actual environment (only JDK 11
  pre-installed, and start.spring.io itself) no longer supports anything older. Validated by
  actually compiling, testing, and running the service against real requests rather than
  assuming the toolchain would behave as documentation for older versions describes — this
  surfaced real API-location differences (Jackson 3 package changes, moved test-autoconfigure
  classes) that were resolved by inspecting the actual jars, not by guessing from memory.
- **Two real bugs caught by actually running the suite**, not just compiling: an ambiguous
  dependency-injection constructor that broke the entire app context, and a shared-mutable-
  state test-isolation bug. Both are root-caused and fixed in `docs/ai-log.md`, not silently
  patched.
- **A real architectural interaction caught during the ambiguous scenario**: deactivating a
  link without evicting its cache entry would have kept it redirecting for up to the cache's
  TTL, silently defeating the feature. Caught before shipping, verified with a real (not
  mocked) request against the running service.

Validation throughout was three-layered: automated tests (unit + integration, run and their
actual pass/fail output checked, never assumed), manual `curl` smoke tests against the actually
running service for every scenario, and explicit self-review of each diff against its task's
acceptance criteria before considering it done.

## Assumptions

Captured in full, per-scenario, in `docs/scenarios.md` and `docs/ai-log.md`. The load-bearing
ones: no auth in v1; anonymous create with no long-URL deduplication (no ownership concept to
scope a dedupe decision against); expiry and deactivation are both soft (row + analytics
history retained); rate limiting is per-IP via the raw remote address, not proxy-aware.

## Limitations

No auth, no distributed rate-limit/cache state, no schema migrations, a coarse (non-library)
User-Agent classifier, a documented-but-unsolved rare edge case where a custom alias could
theoretically collide with a future auto-generated code (fails loudly rather than corrupting
data). All are documented with the reasoning behind accepting them, in
`docs/testing-and-tradeoffs.md`, rather than left implicit.

## What I'd do next with more time

Add auth and scope long-URL deduplication and rate limiting per-user instead of per-IP;
introduce Flyway before any real data exists; move the cache and rate limiter to a shared store
for multi-instance deployment; add a composite-index-aware load test for the analytics rollup
as click volume grows; resolve the custom-alias/generated-code namespace collision properly
(disjoint character ranges) instead of just failing loudly on the rare conflict.
