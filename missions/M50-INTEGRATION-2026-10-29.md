# SERV-M50-01 — Outils document/CSV en lecture seule et contrat durable ADR-002

Dépôt: bubth720/JARVIS-SERVICES
Branche de distribution: mission/m50-integrated-beta-20261029
Base figée de travail: 7f2416dd73b8fb1d60ec87b09fd9d6749001281d

**MISSION, PAS IMPLÉMENTATION.** Cible M50 = application unique avec coeur conversation, documents/CSV locaux et composants indépendants, seulement après qualifications de confiance. Cette mission est une tranche spécifique, distincte des PR lot3/lot4 existantes.

## Commande pour Codex
Ouvrir AGENTS.md si présent; vérifier les SHA, les PR existantes, provenance/CI et les lignes exactes du code. Créer une NOUVELLE branche `codex/m50-*` depuis cette branche de distribution, puis une PR brouillon vers elle. Ne pas modifier ni merger main, ni toucher aux données personnelles, ni relever de permission/SDK/ABI, ni activer connexion sensible sans validation distincte.

## Fichiers prioritaires à inspecter (confirmer leur présence)
- `docs/prefusion/lot4/GITHUB-RECOVERY-REPORT.md`
- `services-portable-api/src/main/java/fr/jarvis/services/api/proposal/CommitAuthorization.java`
- `services-portable-api/src/main/java/fr/jarvis/services/api/proposal/ServicesWirePreflight.java`
- `services-android-adapter/src/main/java/fr/jarvis/services/android/adapter/ServicesAndroidToolPort.java`
- `desktop/src/test/java`

## Étapes détaillées

### 1. A — Baseline lot4

Comparer services #9 et #11, les 13 scénarios de journaux **RAM synthétiques** et les 60 cas instrumentés. Aucun journal durable Android ni autorité CORE réelle n'est validé par ces tests.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 2. B — Première fonction utile sans mutation

Qualifier un adaptateur consommable dans une application de labo pour documents.search et data.csv.normalize, sans transfert de documents vers Internet ou duplicata du stockage NEXUS. Entrée explicitement choisie par utilisateur, CSV 0.10+0.20 exact, interdiction d'un export implicite.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 3. C — ADR-002 versionnée

Rédiger spécification two-journal avec epoch/session/request/generation, preview digest, nonce unique, point de révocation, ordre journal/commit, crash matrix, ACK perdu et UNKNOWN durable. Tester simuleur à disque isolé si faisable, mais **pas de vrai write sur SAF ou activation SAF_EXPORT_UNQUALIFIED**. Présenter décision et attendre accord.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 4. D — Sécurité multi-app

Plan pour signature UID/certificat Android consommateur et permissions, validateur strict, idempotence; ne pas implémenter transport actif sans revue. Vérifier refus en cas de coupure, expiration, permission absente, erreur digest et payload externe.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

### 5. E — Qualification

Réexécuter test JVM/Robolectric, builds/lint, 15 cas API24/25/26/35 et tests de contrat CORE en labo. Source intouchée, logs expurgés, aucun droit productif, PR brouillon et rollback.

**Preuves d'acceptation de la phase :** diff ciblé et réversible, test nominal + contre-exemple, SHA40, commande exacte, sortie CI liée au HEAD, risques de régression et état PASS/BLOCKED/NOT_RUN.

## Règles de stop et critères finals
- D'abord prototypes OFF/testOnly. Le consentement utilisateur à l'architecture ou à des principes n'autorise PAS permissions, authentification de production, migration, import natif, export SAF ou installation physique.
- Préserver contrat historique, bases, voix personnelles, minSdk, permissions et anciennes variantes; voir le dossier central M50.
- Aucune réussite hôte ne qualifie Android réel; aucun résultat d'émulateur ne qualifie un appareil physique.
- Ne jamais masquer des échecs, affaiblir des tests, extrapoler READY, prétendre à un outil natif libéré ou créer de citation synthétique présentée comme réelle.
- Rendre : module, mission, branche, HEAD SHA + tree, tests succès/échecs/skips/non exécutés, CI et PR, blocages, dépendances, rollback, prochaine action.
- Ne jamais fusionner main sans confirmation explicite; un échec bloquant = rapport et STOP.
