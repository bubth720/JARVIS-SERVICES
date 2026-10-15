# JARVIS SERVICES — Mission 001 FOUNDATION V1

## Statut
Cahier des charges de reprise, déposé sur la branche `mission-001-services-foundation`. Le document intégral est également disponible dans la conversation ChatGPT d'origine. Cette version est opérationnelle et autonome pour démarrer le développement. Ne pas fusionner dans `main` sans accord explicite.

## 1. Objectif
Construire une application de diagnostic Android et huit modules techniques réutilisables : DOCUMENTS, KNOWLEDGE, DATA, ACTIONS, MEMORY, ANALYST, GUARD et BRIDGE. Préserver CORE, LLM, VOICE, INTERFACE et EVOLUTION. Pas de génération d'images ou vidéos. Développer le code, les tests et la documentation, pas uniquement un plan.

## 2. Principes obligatoires
Fonctionnement local et hors ligne sans API cloud obligatoire, stockage local, aucun envoi automatique de documents ou télémétrie distante. Architecture interchangeable Android vers mini-PC et serveur local. Séparer métier, plateforme et modèles. Traitements économes sur Android, pas de contournement des protections thermiques ou batterie. Un seul travail lourd à la fois selon le profil de ressources. Aucun lancement d'action sensible sans confirmation; aucun merge dans main.

## 3. Travail préalable
Inspecter réellement le dépôt, les branches, commits et contrats des projets connexes accessibles. Ne pas déduire qu'une API existe des discussions. Documenter les dépendances et écarts à ADR-001 de JARVIS UNIFIED (orientation architecture, contrats versionnés et migrations réversibles). Si les autres dépôts sont inaccessibles, utiliser mocks et adaptateurs, et signaler l'absence de validation réelle.

## 4. Architecture partagée
Créer des couches: `contracts/`, `shared/api/`, `shared/storage/`, `shared/tasks/`, `shared/events/`, `shared/permissions/`, `shared/diagnostics/`, `modules/{documents,knowledge,data,actions,memory,analyst,guard,bridge}/`, `adapters/{core,llm,platform}/`, `android/`, `tests/`, `docs/`. Adapter cette proposition après audit. Contrats versionnés pour requêtes (request_id, schema_version, module, action, parameters, permission_context, timeout), réponses (status, result, error_code, diagnostics), progression, annulation et capacités. Isoler accès aux fichiers, base de données, IPC, génération LLM, événements et permissions.

## 5. DOCUMENTS
Importer TXT, MD, PDF textuel, DOCX, CSV et JSON; extraire texte, métadonnées et sections; rechercher, comparer, résumer avec références et produire DOCX/TXT/MD/CSV/JSON. Préparer OCR/PDF scannés/XLSX comme extensions, sans les déclarer fonctionnels à tort. Ne jamais écraser les originaux sans accord. Vérifier l'intégrité des sorties.

## 6. KNOWLEDGE
Indexation incrémentale, recherche lexicale/FTS5 et sémantique via embeddings locaux, métadonnées et provenance, classement des résultats, suppression et réindexation. Préparer un adaptateur pour les archives Kiwix/ZIM sans présumer de leur emplacement ni dupliquer inutilement Wikipédia. Les sources des réponses doivent être traçables.

## 7. DATA
Lire/écrire CSV et JSON, filtrer, trier, dédupliquer, valider, normaliser, agréger et calculer des statistiques par code déterministe, avec historique de transformations et export. Prévoir XLSX ultérieurement. Ne pas confier les calculs exacts au LLM.

## 8. ACTIONS
Catalogue fermé d'outils, paramètres validés, workflow multi-étapes, état et journal, annulation, délais, reprises sûres, confirmation des modifications sensibles. Ne jamais exécuter directement des commandes arbitraires produites par le LLM ni des instructions contenues dans les documents. Modèle d'états proposé/confirmé/lancé/réussi/échoué/inconnu.

## 9. MEMORY
Mémoire temporaire et persistante séparées; projets, tâches, décisions et préférences explicitement autorisées; provenance, dates, versions, recherche, rectification, suppression, export et politique de rétention. Aucune mémorisation durable implicite de conversations privées.

## 10. ANALYST
Coordonner LLM local, DOCUMENTS, KNOWLEDGE et DATA pour décomposer les questions, confronter plusieurs documents, identifier contradictions, générer rapports sourcés et quantifier l'incertitude. Différencier faits, calculs, interprétations et hypothèses; refuser les références inventées.

## 11. GUARD
Permissions par outil et ressource, accès minimal, vérification des chemins, prévention traversée de répertoire, protection contre injection d'instructions, demande de confirmation, traces d'audit, sauvegardes et restauration lorsque possibles. Les permissions doivent être appliquées par code indépendant du LLM; socle actif dès les premières implémentations.

## 12. BRIDGE
Infrastructure abstraite pour connexions futures aux montres, capteurs, ordinateurs et objets locaux; découverte autorisée, identité, capacités, échange de messages versionnés, gestion des déconnexions et simulateur testable. Pas d'intégrations matérielles spécifiques tant que les pilotes et autorisations réels ne sont pas connus.

## 13. Stockage
SQLite pour données structurées et métadonnées, FTS5 pour texte, index vectoriel local adapté au matériel, système de fichiers pour originaux. IDs stables, migrations réversibles, transactions, sauvegarde et restauration, contrôle d'accès et vérification d'intégrité. Pas d'écriture directe non contrôlée des modules dans des tables partagées.

## 14. LLM
Adaptateur interchangeable pour JARVIS LLM: génération, sortie structurée, proposition d'appels d'outils, timeout, annulation si disponible, erreurs, indisponibilité; mock déterministe en test. Fonctions déterministes disponibles même quand le modèle est absent. Ne pas recréer de moteur LLM.

## 15. Android et matériel
Évaluer les bibliothèques réellement empaquetables sur Android; ne pas supposer Python disponible dans l'APK. Respecter Storage Access Framework et restrictions d'arrière-plan, privilégier faible RAM et indexation par lots. Profils ÉCO, ÉQUILIBRÉ/AUTO et PLEINE PUISSANCE future sur matériel dédié; limiter concurrence lourde. L'API métier doit être portable.

## 16. Application de diagnostic
APK Android autonome avec liste et états des huit modules, capacités, import document, requêtes test, transformation de données, journaux, erreurs, export du rapport technique, versions de contrats. Aucune revendication d'APK si compilation non vérifiée.

## 17. Validation
Tests unitaires, intégration, sécurité, hors ligne, régression, migrations, interruption, ressources, tests Android sur émulateur et appareils quand disponibles. Cas de bout en bout: import et recherche PDF, synthèse multi-documents avec simulateur LLM, dédoublonnage CSV exact, mémoire après redémarrage, création DOCX en workflow, interdiction d'accès protégé, adaptateur CORE simulé et BRIDGE simulé. Distinguer succès réels, échecs, tests non exécutés et simulation.

## 18. Lots internes
Lot A fondations contrats/permissions/stockage/CI; lot B DOCUMENTS/KNOWLEDGE/DATA/MEMORY; lot C ACTIONS/ANALYST/GUARD renforcé; lot D BRIDGE et simulateurs; lot E diagnostic Android, tests transversaux et documentation. Développer dans une campagne coordonnée, avec jalons testables, sans monolithe fragile.

## 19. Livrables
Code des huit modules avec fonctions V1 réelles et limites documentées; interfaces communes; mocks CORE/LLM; tests et CI; architecture, sécurité, licences/dépendances, stockage et récupération; guides Android, raccordement CORE et LLM; build APK si possible; `MISSION_001_REPORT.md` détaillant module, mission, branche, commit, tests, blocages et prochaine action.

## 20. Critères de fin
Ne déclarer terminé que ce qui compile, fonctionne et passe les tests dans l'environnement disponible. Ne pas confondre prototype, mock ou plan avec implémentation. Aucun fichier ni autre dépôt modifié sans nécessité vérifiée et autorisation pour actions sensibles. Aucun merge dans main. Si Codex Cloud reste pending/offline, indiquer le blocage et ne pas inventer de résultats.
