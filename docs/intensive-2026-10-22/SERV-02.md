# SERV-02 — Traitements documentaires composables et analyse sourcée hors ligne

## 1. Référence de mission
**Domaine** SERVICES, dépôt `bubth720/JARVIS-SERVICES`, code existant `mission-002a-android-qualification`, SHA `0f462c8ad5529ba974cefa17fe6562c8d8461d26`.
**Branche documentaire** `plan/intensive-2026-10-22`, branche Codex de code recommandée `feat/serv-02-services`.
**Priorité** P1, **dépendances** : SERV-01. **État** : fiche publiée, exécution non autorisée.

## 2. État GitHub vérifié et acquis
Dépôt réellement développé : modules `documents`, `knowledge`, `data`, `actions`, `memory`, `analyst`, `guard`, `bridge` dans `shared-runtime`; Android diagnostic et SQLite/FTS5. Le rapport 002A/recheck enregistre 59 tests JVM et 11 tests instrumentés sur émulateur, CI réussie ; ces preuves **ne démontrent pas de raccordement CORE**. BRIDGE ne possède pas de transport réel. Cette mission est de création, pas de nouvel audit.

## 3. Objectif produit
Faire des services documentaires une véritable chaîne de production de résultats : extraire, nettoyer, transformer, créer et citer, sans LLM factice.

## 4. Développements substantiels obligatoires
1. Créer pipeline DocumentJob à étapes extract/normalize/segment/transform/export et mapping d'erreurs par étape.
2. Réutiliser PDFBox Android/JVM, le lecteur DOCX sécurisé et les transformations CSV exactes existants ; ajouter Markdown et texte en sortie.
3. Implémenter opérateurs de données typés filtre, tri, agrégation et dédoublonnage avec contrôle de formats/nombres et résultats déterministes.
4. Générer rapport documentaire structuré avec références de source/page/extrait plutôt qu'un résumé présenté comme IA.
5. Exporter au format DOCX et Markdown dans URI choisi par utilisateur, écriture transactionnelle, suivi et annulation.
6. Créer des recettes exécutables multi-documents publiques, réutilisables et paramétrées, sans exécution de scripts utilisateur.

## 5. Architecture interne et séparation des responsabilités
1. DocumentPipeline + PipelineStage + JobProgress
2. DataTransformEngine + DataSchemaValidator
3. SourceCitation et EvidenceReportBuilder
4. ExportWriter et SafUriPort
Conserver les huit modules historiques ; n'ajouter aucune seconde source de vérité MEMORY/NEXUS ni opérateur LLM propre à SERVICES.

## 6. Fichiers cibles
1. Existants : shared-runtime/src/main/java/fr/jarvis/services/modules/{documents/Documents.java,data/Data.java,analyst/Analyst.java}
2. À créer : shared-runtime/src/main/java/fr/jarvis/services/pipeline/{DocumentPipeline,DataTransformEngine,EvidenceReportBuilder}.java
3. À créer : desktop/src/test/java/fr/jarvis/services/PipelineFixtureTest.java et android/src/androidTest/.../PipelineAndroidTest.java
Chemins existants confirmés lorsqu'indiqués « Existants » ; chemins « À créer » proposés et à confronter au dépôt juste avant l'exécution.

## 7. Mise en œuvre, par étapes de code
1. Lister et réemployer fonctions réelles DOCUMENTS/DATA/ANALYST ; éviter parseur XML alternatif non sécurisé.
2. Coder modèle PipelineStage et l'exécuteur de flux avec bornes tailles et récupération d'erreurs.
3. Brancher traitement CSV réel et citations PDF/DOCX via les adaptateurs existants.
4. Ajouter export sous SAF avec fichier temporaire et confirmation de résultat.
5. Tester un exemple PDF+CSV synthétiques générant un rapport Markdown et DOCX ; vérifier son contenu.

## 8. Langages et dépendances
Java/Kotlin uniquement si compatible avec dépôt, PDFBox/SQLite/FTS5/DOCX existants, streaming borné.
Ne pas ajouter de permission INTERNET, micro, stockage global ou bibliothèque non vérifiée. Conserver la vérification Gradle et les fixes Android DOCX de 002A.

## 9. Points de raccordement
SERVICES effectue les actions. NEXUS pourra fournir la recherche via un port de lecture sans partager sa base. Le texte du rapport ne doit pas être présenté comme génération LLM.
Ne pas activer Binder ou IPC CORE/LLM sans accord distinct ; les ports peuvent être construits et testés en autonomie.

## 10. Sécurité et compatibilité
Les URI doivent passer par SAF, les tailles être plafonnées, les traitements annulables, les journaux expurgés, les données d'origine inchangées. Toutes les actions sensibles nécessitent un accord local vérifiable. Conserver minSdk Android, FTS5 natif, XML/DTD strict, signature et règles de sauvegarde. Aucune baisse de budget ou accélération Android arbitraire.

## 11. Livrables exigés
Classes et moteurs compilés et réellement invoqués, scénario CLI/Android de bout en bout avec données synthétiques, tests minimum des vrais adaptateurs, rapport court de résultats exacts, commit de code sur branche dédiée. Une classe vide, une documentation seule ou un scénario uniquement mocké ne suffisent pas.

## 12. Définition de terminé
1. Une vraie commande importe des fichiers de fixture, exécute une transformation et produit des documents lisibles.
2. Les rapports conservent les références d'origine et refusent de citer un extrait inexistant.
3. Toute corruption DOCX/XML ou URI non autorisée entraîne un refus net sans modifier les originaux.
Indiquer séparément ce qui fonctionne sous JVM/émulateur et les capacités non testées physiquement.

## 13. Orchestration entre missions
Ordre bloquant : SERV-01. Ne pas exécuter deux branches SERVICES éditant les mêmes modules au même moment ; intégrer d'abord la précédente dans une base de travail dédiée, sans fusion `main`.

## 14. Restrictions Git et autorisations
La préparation documentaire est autorisée, **pas l'exécution par dépôt de cette fiche**. Pas d'accès à données utilisateur, pas de déploiement, installation, fusion, suppression, migration ou modification sensible des permissions sans autorisation spécifique.

## 15. Rapport final à fournir
Module ; mission ; branche ; base/commit final ; fichiers touchés ; démo exécutable ; tests passés/échoués/ignorés ; risques ; blocages ; prochaine action. Conserver les anciens tests et contrats non remplacés.
