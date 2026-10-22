# SERV-01 — Registre d'outils réel et contrôleur de permissions locales

## 1. Référence de mission
**Domaine** SERVICES, dépôt `bubth720/JARVIS-SERVICES`, code existant `mission-002a-android-qualification`, SHA `0f462c8ad5529ba974cefa17fe6562c8d8461d26`.
**Branche documentaire** `plan/intensive-2026-10-22`, branche Codex de code recommandée `feat/serv-01-services`.
**Priorité** P0, **dépendances** : Aucune. **État** : fiche publiée, exécution non autorisée.

## 2. État GitHub vérifié et acquis
Dépôt réellement développé : modules `documents`, `knowledge`, `data`, `actions`, `memory`, `analyst`, `guard`, `bridge` dans `shared-runtime`; Android diagnostic et SQLite/FTS5. Le rapport 002A/recheck enregistre 59 tests JVM et 11 tests instrumentés sur émulateur, CI réussie ; ces preuves **ne démontrent pas de raccordement CORE**. BRIDGE ne possède pas de transport réel. Cette mission est de création, pas de nouvel audit.

## 3. Objectif produit
Développer le catalogue de capacités et son moteur de dispatch pour rendre les huit modules existants réellement appelables par un orchestrateur, avec permissions explicites, sans Binder inter-APK de production.

## 4. Développements substantiels obligatoires
1. Créer ToolDescriptor versionné contenant ID, schéma d'entrée/sortie, coût mémoire, capacité réseau requise, mode lecture/action et classe de consentement.
2. Construire ToolRegistry persistant/découvrable, résolution par identifiant et contrôle de collision/version ; adapters compatibles avec Actions, Documents, Knowledge, Data, Memory, Analyst, Guard, Bridge.
3. Créer ToolDispatcher async avec délais, annulation propagée, journal minimal non personnel et résultats SUCCESS/REFUSED/FAILED/UNKNOWN.
4. Ajouter ApprovalGate durable à usage unique pour opérations sensibles, sans accepter une auto-approbation venue du LLM.
5. Implémenter trois opérations fonctionnelles : recherche documentaire bornée, transformation CSV exacte et création DOCX après accord ; appeler le vrai code SERVICES existant.
6. Créer une application de démonstration CLI et vue de diagnostic Android facultative.

## 5. Architecture interne et séparation des responsabilités
1. ToolDescriptor/ToolInvocation/ToolResult contract
2. ToolRegistry + ToolResolver
3. PermissionPolicy + ApprovalGate + AuditTrail
4. LocalToolDispatcher et adaptateurs vers modules existants
Conserver les huit modules historiques ; n'ajouter aucune seconde source de vérité MEMORY/NEXUS ni opérateur LLM propre à SERVICES.

## 6. Fichiers cibles
1. Existants à étendre : shared-runtime/src/main/java/fr/jarvis/services/modules/actions/Actions.java, modules/guard/Guard.java, shared/api/Contracts.java et Runtime.java
2. À créer : shared-runtime/src/main/java/fr/jarvis/services/tools/{ToolDescriptor,ToolRegistry,ToolDispatcher,ApprovalGate}.java
3. À créer : desktop/src/test/java/fr/jarvis/services/ToolDispatcherTest.java ; Android UI de démo optionnelle
Chemins existants confirmés lorsqu'indiqués « Existants » ; chemins « À créer » proposés et à confronter au dépôt juste avant l'exécution.

## 7. Mise en œuvre, par étapes de code
1. Lire le modèle de permissions de GUARD et l'audit des contrats, sans copier le rôle du CORE.
2. Définir versionning, types d'erreurs et droits tool-by-tool ; enregistrer les capacités effectivement disponibles.
3. Brancher trois vrais adaptateurs existants et vérifier l'effet utilisateur observable, pas un retour simulé.
4. Ajouter annulation/bornes et workflow approbation locale à identité injectée.
5. Tester collisions, droits refusés, répétition et interruption ; publier preuve CLI.

## 8. Langages et dépendances
Java/JVM, Gradle et bibliothèques SERVICES existantes, AndroidX/SAF déjà présent ; ne pas ajouter de connecteur cloud.
Ne pas ajouter de permission INTERNET, micro, stockage global ou bibliothèque non vérifiée. Conserver la vérification Gradle et les fixes Android DOCX de 002A.

## 9. Points de raccordement
CORE passera des demandes typées via un futur adaptateur opt-in ; UNIFIED reste référentiel des protocoles, pas un runtime ; NEXUS propriétaire de sa base.
Ne pas activer Binder ou IPC CORE/LLM sans accord distinct ; les ports peuvent être construits et testés en autonomie.

## 10. Sécurité et compatibilité
Les URI doivent passer par SAF, les tailles être plafonnées, les traitements annulables, les journaux expurgés, les données d'origine inchangées. Toutes les actions sensibles nécessitent un accord local vérifiable. Conserver minSdk Android, FTS5 natif, XML/DTD strict, signature et règles de sauvegarde. Aucune baisse de budget ou accélération Android arbitraire.

## 11. Livrables exigés
Classes et moteurs compilés et réellement invoqués, scénario CLI/Android de bout en bout avec données synthétiques, tests minimum des vrais adaptateurs, rapport court de résultats exacts, commit de code sur branche dédiée. Une classe vide, une documentation seule ou un scénario uniquement mocké ne suffisent pas.

## 12. Définition de terminé
1. Trois vrais outils existants exécutés via un seul dispatcher et retournent un résultat vérifiable.
2. Un appel sans permission est refusé avant l'accès fichier ; une action approuvée ne peut être rejouée.
3. Les huit modules historiques restent compilables et aucun changement de leurs schémas n'est destructeur.
Indiquer séparément ce qui fonctionne sous JVM/émulateur et les capacités non testées physiquement.

## 13. Orchestration entre missions
Ordre bloquant : Aucune. Ne pas exécuter deux branches SERVICES éditant les mêmes modules au même moment ; intégrer d'abord la précédente dans une base de travail dédiée, sans fusion `main`.

## 14. Restrictions Git et autorisations
La préparation documentaire est autorisée, **pas l'exécution par dépôt de cette fiche**. Pas d'accès à données utilisateur, pas de déploiement, installation, fusion, suppression, migration ou modification sensible des permissions sans autorisation spécifique.

## 15. Rapport final à fournir
Module ; mission ; branche ; base/commit final ; fichiers touchés ; démo exécutable ; tests passés/échoués/ignorés ; risques ; blocages ; prochaine action. Conserver les anciens tests et contrats non remplacés.
