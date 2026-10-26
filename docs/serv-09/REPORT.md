# SERV-09 — Bounded real SAF picker retry (instrumentation only)

**Scope:** one Android instrumentation test fixture. Base SERV-08
`f8976cda0c68449d63a015c89ab1829a852b5e97`.
No SERVICES production runtime, permission, schema, trusted provider, Gradle
dependency or historical test assertion has changed.

## Reproduced failure before this change

- SERVICES run 40404851239 at SERV-08 (Android API35) ran 15 cases,
  13 passed, 2 failed: `source.csv` absent from DocumentsUI, followed by
  an unexpected launcher view in the DOCX selection scenario.
- Previous run 40352356751 on its source head failed one API35 selection
  after 14/15 cases. The picker instability therefore predates SERV-09.
- This does **not** imply FTS5, CORE authority, or production export readiness.

## Change

- `pick()` wraps the existing real DocumentsUI flow `pickOnce()`.
- At most one retry after an assertion during an affirmative test selection.
- The harness injects a real BACK event and relaunches the real system picker.
- No direct provider URI, forged Activity result, permission grant or fake
  source is ever supplied. Failure on retry fails the unchanged test.
- Original failure is kept as a suppressed cause on retry failure.
- The original 15 test cases, positive content hash/readback checks and
  source-preservation assertions stay intact.

## CI and acceptance

Require the existing `SERVICES foundation and Android qualification` workflow:
`desktop:test`, Android build/lint and full 15-case instrumentation on
API24, API25, API26 and API35, **all at the SERV-09 SHA**.
A green JVM/build result never overrides a failed API35 test.
No runtime export is authorized regardless of the outcome.

If API35 remains red, preserve logs and refine root-navigation state machine
on a later dedicated branch. Do not skip tests, extend unlimited retries or
weaken required SAF/permission assertions.

Rollback: revert the sole changed Android test source and this report.
No user databases, source documents or production code touched.
