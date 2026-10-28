# CODEX — JARVIS SERVICES LOT 4 / SERV-PF-03

**Objectif : journal de mutation synthétique et réconciliation UNKNOWN en lecture seule, sans autorité ni export en production.**
**Branche mission :** `mission/adr002-principles-lot4-20261028`.
**Base :** `cfb5f117edc72acd8d5967980d3dfc9dfbf2903f`,
`codex/prefusion-lot3-services`.
**Référence des principes approuvés :** JARVIS-UNIFIED,
`docs/DECISIONS/ADR-002-PRINCIPLES-APPROVED-2026-10-28.md` sur
`mission/adr002-principles-lot4-20261028`.

## Phases à exécuter

**S0 — Photo et régression.** Lire
`docs/prefusion/lot3/ADR-002-PROPOSEE.md`, `docs/prefusion/lot3/REPORT.md`,
`services-portable-api/src/main/java/fr/jarvis/services/api/proposal/CommitAuthorization.java`,
`ServicesWirePreflight.java`,
`desktop/src/test/java/fr/jarvis/services/prefusion/ReceiptContractBench.java`
et `ServicesAndroidToolPort.java`.
Vérifier HEAD, branche parente, toutes les CI, 15 scénarios SAF/API24/25/26/35;
conserver `SAF_EXPORT_UNQUALIFIED` et les schémas historiques.

**S1 — D2 : deux journaux uniquement synthétiques.** Étendre le banc JVM du
lot 3 avec simulation de décisions CORE propriétaire et journal worker
SERVICES séparés. Définir trace canonique bornée:
`PREPARED` / `DISPATCHED` (sans effet) /
`CORE_DECISION` / `COMMIT_STARTED` / `COMMITTED` ou `UNKNOWN`.
Chaque état possède session/request/epoch/generation/version, hash/preview
exact, nonce usage unique, destination, watermark et deadline. Les
séquences `COMPLETED→COMMITTED`, `DISPATCHED→COMMIT_STARTED` et
génération0→wire>=1 ne doivent jamais être déduites implicitement.

**S2 — Crash matrix.** Injecter des coupures à chaque frontière avant et
après écriture des journaux, révocation, perte d'ACK, fermeture échouée,
relecture fautive, retour tardif et kill simulé. Prouver absence de double
effet, aucun retry d'une opération UNKNOWN, séparation des décisions et
refus de réutilisation d'une confirmation, test de concurrence. Ne pas
déduire de l'essai en mémoire une atomicité SQLite multi-processus.

**S3 — D3 : diagnostic sans mutation.** Proposer une fonction laboratoire
`inspectUnknown` qui consulte une destination synthétique **uniquement
en lecture**, contrôle identité, bytes complets, empreinte et version,
et renvoie `INCONCLUSIVE` ou `EVIDENCE_MATCH` sans promouvoir seule
un statut historique en COMMITTED. Autorité CORE réelle, grants et
durabilité restent NON QUALIFIÉS. Ne jamais ouvrir/écrire un URI
personnel; aucune permission supplémentaire.

**S4 — Revue commune.** Comparer protocole au document CORE lot4 et
aux exigences UNIFIED. Produire `docs/prefusion/lot4/REPORT-SERV-PF-03.md`,
matrice de crash, diff, tests exécutés/échoués/skips, suite SAF au SHA
exact, risques et rollback. Draft PR depuis une branche de
développement vers cette branche mission.

## Invariants

- Changer seulement le banc `desktop/src/test`, de nouveaux tests
  ou des documents de conception tant que le transport/versions ne
  sont pas autorisés. Pas de modification de production API ou Gradle
  sans approbation distincte.
- Aucune écriture SAF en production ni raccordement CORE,
  journal utilisateur, purge/retry, déploiement, migration,
  téléchargement, levée de garde ni fusion `main`.
- Des tests synthétiques passants ne valent **pas** preuve de SQLite
  durable, Binder UID, FTS5, permission Android ou moteur natif.
- **STOP** si le scénario exige une autorité live, une signature
  Android, un vrai document ou l'adoption définitive de l'ordre de commit.
