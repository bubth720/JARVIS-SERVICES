# Mission Codex SERV-PF-01 — Résoudre API35 SAF et finaliser la revue des reçus

Emplacement : missions/PREFUSION-2026-10-26.md dans bubth720/JARVIS-SERVICES
Branche mission : mission/prefusion-gates-20261026
Base observée (à revérifier) : 7c7fc95ad19e10de877b14fb5d3fe7f53b27d3e4

## Contrat d'exécution
Lire AGENTS.md s'il existe, vérifier les refs et les PR avant tout changement. Créer une nouvelle branche de développement depuis la mission. Travail 100 % local/hors ligne au runtime, protections Android/thermiques, contrats et fonctionnalités historiques préservés. Aucun merge main, migration irréversible, activation d'écriture, changement majeur d'architecture, données utilisateur dans Git, suppression/skip de tests. Toute décision sensible => STOP et demande d'accord.

## Étapes exécutables

### 1. Diagnostic

Lire docs/fusion-02/REPORT.md, docs/serv-08/REPORT.md, docs/serv-09/REPORT.md; comparer runs 40352356751, 40404851239, 40410812418 au HEAD. Identifier précisément les scénarios DocumentsUI/API35 et les causes sans attribuer le vert d'un ancêtre au code actuel.

Livrables : code et tests ciblés, diff, commit séparé, CI exacte au SHA, rapport d'échec et retour arrière pour cette étape.

### 2. DocumentsUI

Sur services-android-adapter/src/androidTest/java/fr/jarvis/services/android/adapter/FusionSafInstrumentedTest.java, fiabiliser le choix réel via Activity Result, état UI et synchronisation; refaire les chemins en erreur. Pas d'URI fabriquée, de grant implicite, de skip, de retry illimité ou de faux résultat. Logs expurgés.

Livrables : code et tests ciblés, diff, commit séparé, CI exacte au SHA, rapport d'échec et retour arrière pour cette étape.

### 3. Vérification exhaustive

Exécuter ./gradlew :desktop:test --no-daemon, build et lint Android, puis les 15 tests instrumentés API24,25,26,35. Exiger zéro échec/skip au HEAD, hash complet du nouveau document, source intacte, refus des permissions, revocation, UNKNOWN sans réessai et distinct UID fixture/worker.

Livrables : code et tests ciblés, diff, commit séparé, CI exacte au SHA, rapport d'échec et retour arrière pour cette étape.

### 4. Autorité de commit

Étudier services-portable-api/src/main/java/fr/jarvis/services/api/proposal/CommitAuthorization.java, ServicesWirePreflight.java et ServicesAndroidToolPort.java. Proposer contrat à CORE/UNIFIED: génération 0 non compatible wire >=1, COMMIT_STARTED distinct de DISPATCHED, consentement unique, revocation avant effet, persistance et ACK incertain. Conserver SAF_EXPORT_UNQUALIFIED et toute mutation OFF.

Livrables : code et tests ciblés, diff, commit séparé, CI exacte au SHA, rapport d'échec et retour arrière pour cette étape.

### 5. Catalogue et rapport

Lister 17 outils historiques vs 2 portés sur API24, détailler format/limite/permission/test/capacité. Préparer PR brouillon et rapport complet. Contrat nouveau/activation nécessite accord.

Livrables : code et tests ciblés, diff, commit séparé, CI exacte au SHA, rapport d'échec et retour arrière pour cette étape.

## Critères d'acceptation
Une étape est validée seulement par tests réellement exécutés, aucun résultat inventé, conservation des données et comportement OFF/refus sûr quand une capacité n'est pas qualifiée. Les tests JVM, Robolectric, Android émulateur et matériel physique ne sont jamais confondus.

## Restitution Codex
Module | mission | branche | commit | PR brouillon | commandes/tests avec nombres et échecs/skips | blocages | compatibilité | rollback | prochaine action. Ne fermer la mission complète que si les critères réels sont satisfaits. Si blocage ou approbation requise, livrer le rapport et s'arrêter. Ne pas fusionner main.
