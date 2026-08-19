# URL Shortener — Project Notes

## What this is
A URL shortener service (core APIs, analytics, reliability features), built with
**AI-assisted, engineer-led execution** — requirement understanding, task decomposition,
disciplined AI use with traceability, and validation/risk control. The project plan lives in
`docs/final-summary.md` (once written).

Stack: Java 21, Spring Boot 3, Maven, JUnit 5, H2 (test/local) / Postgres (prod profile).

## Subagent pipeline

This project defines five subagents in `.claude/agents/`, one per stage of the engineering
lifecycle. Use them in this order for any non-trivial task — greenfield, brownfield, or an
ambiguous ask — not just during initial build-out:

1. **requirement-analyst** — normalizes the ask, surfaces ambiguity as explicit assumptions,
   produces a dependency-ordered task breakdown with acceptance criteria. Always start here for
   anything beyond a one-line trivial fix.
2. **codebase-reasoner** — brownfield only. Read-only impact analysis (change surface, data
   flow, blast radius, existing test coverage, risk rating) before any existing code is touched.
   Skip for genuinely greenfield tasks.
3. **spring-implementer** — writes the actual code, following the task spec and (if brownfield)
   the impact analysis. Appends a traceability entry to `docs/ai-log.md`.
4. **test-engineer** — unit + integration tests, with emphasis on edge/failure cases, not just
   happy path. Actually runs the tests and reports real results. Appends to `docs/ai-log.md`.
5. **quality-gatekeeper** — final gate: build/lint/test run, security + correctness self-review,
   flags high-impact changes (schema, auth, redirect hot path, breaking API changes) that need
   the user's explicit sign-off before being "done." Appends to `docs/ai-log.md`.

For a deep correctness bug-hunt beyond the gate check, use the built-in `/code-review` skill —
quality-gatekeeper complements it, not replaces it.

## Traceability

`docs/ai-log.md` is the running record of AI-generated/edited/rejected work with rationale,
built up by the agents above. Don't hand-edit it away — it's how we keep AI-assisted changes
auditable: what was generated, what was changed and why, what was rejected.

## Ground rules (apply to every agent and to direct edits)

- Controller → service → repository → entity layering; DTOs never leak entities over the API.
- The redirect hot path (`GET /{code}`) stays synchronous-and-fast — no blocking analytics
  writes on it.
- No secrets/credentials in code or comments. No PII beyond what's explicitly scoped.
- High-impact changes (schema/migrations, auth, redirect hot path, breaking API contracts)
  require explicit human sign-off — an agent flags these, it doesn't self-approve them.
- The engineer (user) owns correctness and production-readiness of everything that ships, AI
  output included.
