# JARVIS SERVICES — MISSION 002A
## Qualification Android et fiabilisation de FOUNDATION V1

**Dépôt :** `bubth720/JARVIS-SERVICES`  
**Branche exclusive :** `mission-002a-android-qualification`  
**Base exacte :** `075158b755000f8c6d8e37747cec9de8221daa72`  
**Interdictions :** aucune fusion dans `main`, aucune modification des autres dépôts, aucun déploiement sur appareil personnel sans autorisation.

### Objectif
Qualifier sur environnement Android réel ou émulateur le diagnostic JARVIS SERVICES livré en Mission 001, corriger les défauts vérifiés, rendre les validations CI reproductibles et documenter les risques. **Ne pas ajouter OCR, XLSX, ZIM, matériel BRIDGE ni le raccordement CORE/LLM réel** dans cette mission.

### 1. Audit initial
- Inspecter la branche, vérifier le SHA de base, lire `MISSION_001_REPORT.md`, `docs/validation/mission-001.json`, `docs/BUILD_ANDROID.md`, `docs/SECURITY.md` et `.github/workflows/verify.yml`.
- Vérifier l'inventaire des trois tests instrumentés et la compatibilité SDK/émulateur.
- Relever les limites de preuve et les avertissements déjà documentés. Ne pas assimiler compilation à exécution.

### 2. Tests Android instrumentés
- Préparer un émulateur Android API 26 ou plus récent, sans contourner les protections système; privilégier une configuration CI exécutable sans accélération matérielle si nécessaire.
- Exécuter les **3 tests Android instrumentés** : démarrage d'Activity, SQLite/FTS5 natif Android, absence de permission INTERNET.
- Si environnement indisponible ou impossible, consigner précisément les tentatives, commandes, logs et blocage; **ne pas déclarer ces tests réussis**.
- Ajouter, seulement si faisable, des vérifications sur SAF/import PDF, export de rapport, refus d'accès non autorisé et restauration isolée, en évitant tests fragiles dépendant de fichiers personnels.
- Ne pas installer sur POCO X8 Pro ou Redmi Pad SE sans confirmation explicite.

### 3. Régressions et sécurité
- Relancer les 55 tests JVM existants et les builds `:android:assembleDebug`, `:android:assembleAndroidTest`, `:android:lintDebug` avec les outils documentés.
- Tester absence de réseau obligatoire, non-écrasement des originaux, grants et confirmations GUARD, séparation mémoire persistante/volatile, échec sûr des workflows et intégrité des migrations.
- Vérifier les versions des dépendances et le packaging final, sans contourner TLS ou les contrôles d'intégrité.
- Examiner les 12 avertissements Lint signalés en Mission 001 : 10 `SetTextI18n`, 1 `DataExtractionRules`, 1 `TrustAllX509TrustManager` dans dépendance. Corriger les avertissements applicatifs justifiés; documenter précisément les avertissements tiers restants. **Ne pas diminuer la sévérité Lint pour obtenir artificiellement zéro avertissement**.
- Préserver les restrictions thermiques, batterie et RAM.

### 4. Qualification fonctionnelle et ressources
- Tester des scénarios courts entièrement locaux : import PDF textuel, recherche FTS5, export DOCX non destructif, transformations CSV exactes, mémoire après redémarrage, workflow soumis à permission, BRIDGE simulé.
- Si émulateur accessible, consigner durée, pic mémoire et stabilité sur jeux de données représentatifs; séparer mesures émulées des mesures sur matériel réel. Ne pas inventer de gains énergétiques.
- Vérifier que les preuves/exports ne contiennent pas de données privées des fichiers importés.

### 5. GitHub Actions
- Lire la configuration CI; vérifier les résultats distants réellement disponibles.
- Corriger les blocages reproductibles de compilation/test; conserver des étapes distinctes pour tests JVM, build APK, lint et instrumentation.
- Ne pas considérer un workflow défini mais jamais exécuté comme une réussite.
- Produire si possible des artefacts APK debug et AndroidTest avec SHA-256, taille et contexte de build, sans diffusion de production.

### 6. Rapport et preuves
Créer `reports/002a/REPORT.md` et `reports/002a/validation.json` avec :
- module, mission, branche, SHA de départ, SHA final;
- fichiers modifiés et justification, compatibilité, risques de régression;
- commandes réellement exécutées et résultats : réussites, échecs, sautés, non exécutés;
- statut de chacun des 3 tests instrumentés;
- avertissements Lint avant/après et analyse de dépendances;
- résultats GitHub Actions (URLs si disponibles) et artefacts;
- mesures de ressources sourcées ou « non mesuré »;
- blocages et prochaines actions, notamment intégration CORE/LLM prévue en 002B.

### Critères d'acceptation
1. Aucun changement régressif aux huit modules, contrats versionnés et fondations existantes.
2. Les 55 tests JVM restent réussis; builds Android et lint vérifiés, ou blocage précisément documenté.
3. Les 3 tests instrumentés sont exécutés et rapportés distinctement **si un appareil/émulateur est disponible**. Sinon la qualification Android reste **partielle**, sans faux succès.
4. Aucun réseau cloud indispensable, aucune permission ou fuite de données ajoutée.
5. Rapport de preuve et documentation lisibles; aucun merge de `main`.

### Consigne d'exécution
Ne te limite pas à un plan : audite, exécute, corrige, réexécute les tests et publie les commits sur `mission-002a-android-qualification`. Toute modification hors dépôt ou installation sur appareil utilisateur nécessite autorisation. Distingue clairement ce qui est testé sur JVM, émulateur et matériel réel.
