# ADR-002 LOT4 — GitHub recovery / SERVICES

Base mission: 13242bca1761a5f71bd86e052db4bc7d1909df37
Branch: feat/lot4-offline-recovery-20261028
Scope: desktop/src/test/java/fr/jarvis/services/prefusion/TwoJournalLot4Test.java only + CI workflow.

13 deterministic JUnit5 scenarios simulate two separate CORE and SERVICES append-only RAM journals, consent consumption, dispatcher, revocation, start barrier, synthetic effect counter, crash before owner append, ACK lost, no retry, and UNKNOWN inspection without mutating either journal. No real ContentProvider, Android UID, disk durability or privileged token. The tests do not authorize production export, and the existing SAF_EXPORT_UNQUALIFIED gate remains unchanged.

Acceptance: full :desktop:test passes at exact head; existing Android CI must be independently required before production discussions. Future actual journaling and readback require another user approval. No merge/main change.
