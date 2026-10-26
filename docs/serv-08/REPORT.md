# SERV-08 / Proposed wire preflight, no runtime activation

**Base:** FUSION-02 SERVICES `9dfbf60a908a7b44030ef060af4c164e349887be`. Current baseline CI at the selected SHA had failed; a green CI on ancestor `10cdce58` is not treated as passing the selected SHA.

## Added
- `ServicesWirePreflight` is an *additive review helper* and never called by the production `ServicesAndroidToolPort`.
- It identifies local proposal generation 0 and Java values above 2^53−1 as incompatible with UNIFIED draft wire v2. It does not shift generations or touch receipt persistence.
- `COMMIT_STARTED` cannot be cast to `DISPATCHED`; `COMPLETED` cannot be presented as `COMMITTED`.
- Even on valid values or readback, result remains `REVIEW_REQUIRED`; no authority grant, connector, SAF export or status READY is implemented.
- New desktop unit tests cover the boundaries and negative state mappings.

## Tests and stopping conditions
- Run `./gradlew :desktop:test --no-daemon`, Android build/port regressions and existing FUSION-02 SAF emulators in GitHub Actions.
- Any failure is reported without suppressing the prior baseline failure.
- `SAF_EXPORT_UNQUALIFIED` remains OFF in production, independently of the test laboratory.
- No data or schema migrations, changes to old tests, CORE edits, permission relaxation or merger into main.

**Rollback:** revert only the new Java class, its test and this report.

**Next:** owner approval of generation semantics and state/receipt protocol, then an isolated CORE adapter implementation and device qualification.
