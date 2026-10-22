# SERV-03 — Workflow local résumable et BRIDGE hors ligne désactivé par défaut

## 1. Référence de mission
**Domaine** SERVICES, dépôt `bubth720/JARVIS-SERVICES`, code existant `mission-002a-android-qualification`, SHA `0f462c8ad5529ba974cefa17fe6562c8d8461d26`.
**Branche documentaire** `plan/intensive-2026-10-22`, branche Codex de code recommandée `feat/serv-03-services`.
**Priorité** P2, **dépendances** : SERV-01,SERV-02,UNI-01. **État** : fiche publiée, exécution non autorisée.

## 2. État GitHub vérifié et acquis
Dépôt réellement développé : modules `documents`, `knowledge`, `data`, `actions`, `memory`, `analyst`, `guard`, `bridge` dans `shared-runtime`; Android diagnostic et SQLite/FTS5. Le rapport 002A/recheck enregistre 59 tests JVM et 11 tests instrumentés sur émulateur, CI réussie ; ces preuves **ne démontrent pas de raccordement CORE**. BRIDGE ne possède pas de transport réel. Cette mission est de création, pas de nouvel audit.

## 3. Objectif produit
Passer de la simulation BRIDGE à une file de tâches réelle, locale et transactionnelle, puis préparer une réplication inter-appareils optionnelle sans l'activer avant qualification de sécurité.

## 4. Développements substantiels obligatoires
1. Créer WorkflowDefinition avec étapes réelles du ToolRegistry, dépendances DAG bornées, délais et états PENDING/RUNNING/WAITING_APPROVAL/FAILED/SUCCEEDED.
2. Implémenter scheduler local résumable après crash, idempotency keys, et journal append-only ne stockant pas les documents personnels.
3. Gérer compensation par étape (suppression de sortie temporaire uniquement) et approbation humaine pour actions modifiant des données.
4. Créer file Outbox/Inbox transactionnelle locale avec vecteurs d'état, digests, séquences et reçus ; les transports restent désactivés.
5. Coder adaptateur LoopbackBridge réel sur deux processus de laboratoire pour tester envoi/réception sur données synthétiques, **sans authentification de production revendiquée**.
6. Préparer interface TransportPort à implémenter plus tard avec appairage signé/chiffré et refus par défaut.

## 5. Architecture interne et séparation des responsabilités
1. WorkflowEngine, StateStore, RetryPolicy et StepExecutor
2. ActionConsentGate et CompensationJournal
3. BridgeOutbox, BridgeInbox, LocalLoopbackTransport
4. PeerPermissionPolicy, inactive until approved
Conserver les huit modules historiques ; n'ajouter aucune seconde source de vérité MEMORY/NEXUS ni opérateur LLM propre à SERVICES.

## 6. Fichiers cibles
1. Existants : modules/actions/Actions.java, modules/bridge/Bridge.java, modules/guard/Guard.java et shared/tasks/Task.java
2. À créer : shared-runtime/src/main/java/fr/jarvis/services/workflow/{WorkflowEngine,WorkflowDefinition,WorkflowStateStore}.java
3. À créer : shared-runtime/src/main/java/fr/jarvis/services/bridge/{BridgeOutbox,BridgeInbox,TransportPort}.java, tests d'intégration
Chemins existants confirmés lorsqu'indiqués « Existants » ; chemins « À créer » proposés et à confronter au dépôt juste avant l'exécution.

## 7. Mise en œuvre, par étapes de code
1. Créer machine à états résumable et persister chaque transition durablement avant effet externe.
2. Raccorder de vraies opérations du ToolRegistry et un refus des cycles/étapes non autorisées.
3. Implémenter compensation sûre et consentement par action, pas d'auto-retry d'action ambigüe.
4. Écrire outbox/inbox durable sur SQLite et transport loopback lab avec états de rejet.
5. Fournir démo de workflow interrompu/repris et de transfert loopback, tests de déduplication et refus.

## 8. Langages et dépendances
Java/JVM, SQLite local, scheduler sans service permanent sur Android; crypto/transport réel après revue séparée.
Ne pas ajouter de permission INTERNET, micro, stockage global ou bibliothèque non vérifiée. Conserver la vérification Gradle et les fixes Android DOCX de 002A.

## 9. Points de raccordement
CORE ne peut lancer le workflow qu'après adaptation et validation de ses permissions ; NEXUS conserve la résolution des conflits de connaissance ; aucun pair physique connecté ici.
Ne pas activer Binder ou IPC CORE/LLM sans accord distinct ; les ports peuvent être construits et testés en autonomie.

## 10. Sécurité et compatibilité
Les URI doivent passer par SAF, les tailles être plafonnées, les traitements annulables, les journaux expurgés, les données d'origine inchangées. Toutes les actions sensibles nécessitent un accord local vérifiable. Conserver minSdk Android, FTS5 natif, XML/DTD strict, signature et règles de sauvegarde. Aucune baisse de budget ou accélération Android arbitraire.

## 11. Livrables exigés
Classes et moteurs compilés et réellement invoqués, scénario CLI/Android de bout en bout avec données synthétiques, tests minimum des vrais adaptateurs, rapport court de résultats exacts, commit de code sur branche dédiée. Une classe vide, une documentation seule ou un scénario uniquement mocké ne suffisent pas.

## 12. Définition de terminé
1. Workflow à trois étapes produit un vrai résultat, survit à arrêt et reprend sans doubler les effets.
2. Outbox/inbox exécutent réellement un échange loopback local avec déduplication et accusé.
3. Aucun réseau ou sync matérielle, aucun service exporté avant consentement/qualification explicite.
Indiquer séparément ce qui fonctionne sous JVM/émulateur et les capacités non testées physiquement.

## 13. Orchestration entre missions
Ordre bloquant : SERV-01,SERV-02,UNI-01. Ne pas exécuter deux branches SERVICES éditant les mêmes modules au même moment ; intégrer d'abord la précédente dans une base de travail dédiée, sans fusion `main`.

## 14. Restrictions Git et autorisations
La préparation documentaire est autorisée, **pas l'exécution par dépôt de cette fiche**. Pas d'accès à données utilisateur, pas de déploiement, installation, fusion, suppression, migration ou modification sensible des permissions sans autorisation spécifique.

## 15. Rapport final à fournir
Module ; mission ; branche ; base/commit final ; fichiers touchés ; démo exécutable ; tests passés/échoués/ignorés ; risques ; blocages ; prochaine action. Conserver les anciens tests et contrats non remplacés.
