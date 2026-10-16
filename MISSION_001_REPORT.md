# MISSION 001 — SERVICES FOUNDATION V1

Date : 16 octobre 2026. Module : JARVIS SERVICES. Dépôt : `bubth720/JARVIS-SERVICES`.
Branche exclusive : `mission-001-services-foundation`.

Livraison : huit modules implémentés, tests automatisés et diagnostic Android compilé.
**La qualification Android physique et les raccordements réels CORE/LLM ne sont pas terminés.**
Les simulations, compilations et tests JVM ci-dessous sont des preuves distinctes.
Aucun merge dans main, aucun déploiement utilisateur et aucun autre dépôt modifié.

## Base, audit et jalons

Cahier des charges lu intégralement à la base `23b8fd9689175118baea2727ff6b45221014aede`.
L'audit en lecture seule des branches UNIFIED, CORE réel et LLM est détaillé dans
[CONTRACT_AUDIT.md](docs/CONTRACT_AUDIT.md). L'ADR-001 est respectée : CORE reste
l'autorité, aucun moteur LLM recréé, aucun choix IPC définitif, aucune migration legacy.
Les contrats SERVICES sont internes, séparés des contrats fermés UNIFIED proposés.

| Commit | Lot livré |
|---|---|
| cb08516 | Fondations : contrats versionnés, permissions, SQLite, tâches et premiers tests |
| 0bd9d7b | DOCUMENTS, KNOWLEDGE, DATA, MEMORY et PDF JVM |
| d9dead8 | ACTIONS, ANALYST, BRIDGE, adaptateurs simulés, récupération et tests transversaux |
| 7ef8428 | Application Android, SAF, instrumentation, CI et script de build Cloud |
| 285c3bc | Intégrité des dépendances, isolation sauvegarde Android, helpers PDF actualisés et parcours ANALYST coordonné |
| 494d33d | Parcours en lots de huit, provenance des hits et résultats documentaires compacts |

Source finale testée : `494d33d`. Le SHA du commit documentaire portant ce rapport
se lit dans l'historique de la branche ; il ne change pas les sources testées.
La publication GitHub jusqu'à `285c3bc` a été confirmée par `git ls-remote` ; le
rapport de livraison de la conversation confirme également le dernier push complet.
`main` observée à `c9f94ad2fa219a5e09cda92fb451aca606ee1646`, sans modification.

## Fonctions réellement implémentées

| Module | Fonctionnement V1 livré | Limites explicites |
|---|---|---|
| DOCUMENTS | TXT/MD/PDF textuel/DOCX/CSV/JSON, originaux immuables, empreintes, sections/offsets, recherche, comparaison, résumé extractif référencé, export DOCX/TXT/MD/CSV/JSON vérifié | Pas OCR/PDF scanné/XLSX, pas mise en page avancée ni qualification de tout PDF hostile ; sections par lignes, pas références de pages inventées |
| KNOWLEDGE | Index incrémental, FTS5/BM25, vecteurs/cosinus locaux avec provenance, concepts FR/EN, suppression et réindexation par lots | Sémantique limitée à 12 groupes de synonymes puis hash lexical ; collisions possibles, pas d'encodeur neuronal qualifié. ZIM : port seul |
| DATA | CSV avec champs quotés/multilignes, JSON tabulaire, validation, filtre/tri/dédoublonnage/normalisation, agrégats et statistiques déterministes, historique et export | 5 000 lignes/100 colonnes ; sommes décimales exactes, moyenne DECIMAL128 annoncée ; pas XLSX |
| ACTIONS | Catalogue fermé document.export, proposition/confirmation/lancement/résultat persistés, workflow, journal, annulation/délais coopératifs, récupération unknown sans rejeu | Aucun shell ni exécution d'instructions documentaires/LLM ; reprise par nouveau workflow confirmé après inspection, pas d'auto-réessai ni rollback des sorties déjà réussies |
| MEMORY | Volatile séparé, projets/tâches/décisions/préférences persistants confirmés, provenance/date/version, recherche/correction/suppression/export/rétention | Aucune mémorisation implicite ; expiration contrôlée à la lecture, purge explicite, pas de daemon Android |
| ANALYST | Recherche KNOWLEDGE, preuves DOCUMENTS, comparaison et conflits candidats, calcul DATA exact, plan fixe inspectable, mock LLM, citations validées et couverture quantifiée | Pas de LLM réel ; pas d'agent de planification général ; pas de preuve de vérité des sources ni de probabilité de confiance arbitraire |
| GUARD | Grants indépendants du LLM, racine privée, anti-traversée/liens, confirmation identité/opération/contenu à usage unique et TTL, audit borné, sauvegarde/restauration contrôlée | Hôte de confiance en processus, pas d'authentification IPC ni de protection face à root/même UID ; sauvegarde logique en clair limitée en taille |
| BRIDGE | Transport abstrait, découverte autorisée, identité/grants par pair, capacités/messages versionnés et corrélés, déconnexion et simulateur echo | Pas de pilote matériel, Bluetooth ou découverte réseau réelle |

`shared-runtime` ne dépend pas d'Android, JDBC ou d'un moteur LLM. Les adaptateurs
plateforme fournissent SQLite/PDF. Les fonctions déterministes restent disponibles
avec `LlmPort.Unavailable`. `CoreAdapter` est une façade interne simulée, pas un
raccordement inter-applications. BRIDGE n'est testé qu'avec un pair simulé.

## Diagnostic Android

Application native Java minSdk26/targetSdk35, pas de Python dans l'APK. Huit états
et capacités, import SAF, recherches texte/FTS5, résumé, comparaison multi-documents
avec mock LLM explicite, transformation CSV, préférence confirmée, workflow DOCX
confirmé, echo BRIDGE, annulation et export du rapport technique sans contenu privé.
Pas de permission applicative, notamment aucune permission INTERNET. Sauvegarde
Android désactivée, exclusions cloud et transfert appareil explicitement déclarées.

Un worker et un bail lourd par processus, admission RAM/batterie/température,
annulation hors premier plan. ECO/AUTO : import 512 Kio, BALANCED : 2 Mio. AUTO
reste conservateur ; pleine puissance future non activée. Les parcours du corpus
sont paginés par huit ; la recherche renvoie des extraits, pas les corps complets.
Les protections OS ne sont ni modifiées ni contournées. Ce n'est pas un arbitre
global CORE/LLM, et les gains RAM/batterie réels ne sont pas mesurés sur appareil.

APK debug produit dans `android/build/outputs/apk/debug/android-debug.apk`.
APK d'instrumentation dans `android/build/outputs/apk/androidTest/debug/`.
Empreintes et tailles exactes : [preuve JSON](docs/validation/mission-001.json).
Les APK restent des sorties locales ignorées par Git ; le workflow CI est préparé
pour les publier comme artefacts, sans résultat distant revendiqué. Pas de signature
production ni d'installation sur téléphone/tablette.

## Validation réelle

Commande finale, réexécutée après les corrections, JDK21/Gradle8.13 sous Linux x86_64 :

```sh
scripts/cloud-gradle.sh --offline -PwithAndroid :desktop:test :android:assembleDebug :android:assembleAndroidTest :android:lintDebug --no-daemon
```

Les variables JAVA_HOME/ANDROID_HOME/ANDROID_USER_HOME/GRADLE_USER_HOME utilisées
sont documentées dans [BUILD_ANDROID.md](docs/BUILD_ANDROID.md). Le wrapper et les
empreintes des dépendances sont vérifiés, TLS conservé. Aucun contrôle d'intégrité
n'a été désactivé et aucun checksum attendu n'a été remplacé pour forcer un succès.

Résultat final détaillé et noms des tests : [docs/validation/mission-001.json](docs/validation/mission-001.json).

- **55 tests JVM réussis, 0 échec, 0 erreur, 0 saut.** FoundationTest : 18 ; ModulesTest : 36 ; BundledStorageTest : 1.
- PDF réel généré avec PDFBox, importé puis recherché via FTS5 ; scan sans texte refusé OCR_REQUIRED.
- DOCX créé, relu, empreinte vérifiée ; original et fichier existant jamais écrasés.
- CSV exact (0.1 + 0.2 = 0.3), champs CSV complexes, JSON, validation et historique.
- Mémoire retrouvée après fermeture/réouverture SQLite ; confirmation, rectification, suppression et expiration.
- Synthèse multi-documents avec mock, contradictions candidates, citations inventées refusées, plan KNOWLEDGE/DOCUMENTS/DATA/LLM simulé testé.
- Workflow confirmé, annulation, refus de rejeu, état unknown après interruption simulée.
- Traversées, chemins absolus, symlinks, droits insuffisants, DTD/XXE, bornes et concurrence lourde testés.
- Migrations, rollback, intégrité, snapshot/FTS5, sauvegarde/restauration d'originaux et backup corrompu testés.
- Parcours représentatif exécuté avec sockets interdites par SecurityManager de test JDK21 ; ce mécanisme ne fait pas partie du runtime et devra être remplacé pour JDK24+.
- Code exact de l'adaptateur SQL Android exécuté avec SQLite natif JVM, FTS5 et rollback vérifiés ; distinct d'un test JNI Android.
- APK applicatif et APK de tests assemblés. Signature debug v2 vérifiée par apksigner ; permissions de l'APK applicatif contrôlées par aapt : aucune.
- Lint sans erreur, **12 avertissements conservés** : 10 SetTextI18n de diagnostic, 1 dépréciation allowBackup malgré règles d'exclusion explicites, 1 helper TLS trust-all dans Bouncy Castle. Aucun appel réseau applicatif ni permission réseau ; aucun trust-all ajouté au code ou au build. Bouncy Castle a été aligné à 1.86. Pas de scan de vulnérabilités exhaustif revendiqué.

Les étapes Gradle NO-SOURCE/SKIPPED de shaders ou prégénération ne sont pas des tests
réussis ni des tests ignorés JUnit. L'instrumentation est comptée séparément.

**Non exécutés :** les trois tests instrumentés (SQLite Android, absence INTERNET,
démarrage de l'Activity), tests sur émulateur/appareils, stress thermique/batterie,
interopérabilité CORE/LLM réelle, pilote BRIDGE, coupure électrique physique et
validation CI distante. Aucun émulateur/appareil ni /dev/kvm fourni par ce Cloud.

## Incidents résolus et limitations de build

Le fetch initial ne créait pas une remote-tracking ref ; création de branche depuis
FETCH_HEAD, sans reset. Le JRE initial ne fournissait pas javac ; JDK vérifié installé.
Java ne reprenait pas le proxy système ; le script de build le transmet en conservant
la chaîne CA. Le répertoire Android utilisateur initial était non inscriptible ;
ANDROID_USER_HOME local résout le build/signing. L'ancienne API de test Android a été
remplacée par AndroidX, puis instrumentation compilée. Les premières erreurs de
compilation ont été corrigées avant les résultats ci-dessus.

Avertissements de tooling restants : initialisation des métriques AGP vers un home
non inscriptible, platform-tools non installé (pas d'adb/appareil utilisé), API Gradle
à migrer avant Gradle9 et SecurityManager de test déprécié sur JDK21. Ils ne sont pas
cachés ni comptés comme validation matérielle. Les bibliothèques PDF/SQLite natives
et les dépendances doivent être réévaluées avant diffusion Android de production.

## Blocages et prochaines actions

Aucun blocage restant pour les tests JVM et la construction/lint de cette livraison.
La clôture de qualification complète requiert :

1. Exécuter les trois tests instrumentés sur un émulateur de test, puis qualifier
   POCO X8 Pro et Redmi Pad SE avec autorisation distincte pour toute installation.
2. Valider le mapping des contrats avec CORE/LLM et l'identité/transport local,
   implémenter et tester les vrais adaptateurs, sans modifier les autres dépôts
   dans le cadre de cette mission. La source existante ne prouve aucun IPC actif.
3. Brancher un encodeur sémantique local qualifié derrière Embeddings si une
   sémantique générale est requise ; conserver le mode déterministe léger.
4. Qualifier parseurs malformés, annulation native, RAM et batterie ; enrichir
   le partage SAF des sorties et la localisation UI. Le DOCX de diagnostic reste
   actuellement dans le stockage privé ; seul le rapport technique a un export SAF.
5. OCR/XLSX/ZIM et matériel BRIDGE : extensions ultérieures, pas livrées à tort.
6. Observer la CI distante après push, produire les notices/SBOM complètes avant
   diffusion et choisir une signature de production séparément. Aucun merge main automatique.

## Fichiers ajoutés ou modifiés

Inventaire par rapport à la base de mission, hors artefacts ignorés. Le cahier des
charges d'origine n'a pas été modifié.

- `.github/workflows/verify.yml`
- `.gitignore`
- `MISSION_001_REPORT.md`
- `README.md`
- `android/build.gradle`
- `android/src/androidTest/java/fr/jarvis/services/android/OfflineSmokeTest.java`
- `android/src/main/AndroidManifest.xml`
- `android/src/main/java/fr/jarvis/services/android/AndroidDatabase.java`
- `android/src/main/java/fr/jarvis/services/android/AndroidPdfText.java`
- `android/src/main/java/fr/jarvis/services/android/MainActivity.java`
- `android/src/main/res/drawable/ic_launcher.xml`
- `android/src/main/res/xml/data_extraction_rules.xml`
- `build.gradle`
- `contracts/v1/cancellation.schema.json`
- `contracts/v1/capabilities.schema.json`
- `contracts/v1/progress.schema.json`
- `contracts/v1/request.schema.json`
- `contracts/v1/response.schema.json`
- `desktop/build.gradle`
- `desktop/src/main/java/fr/jarvis/services/platform/JdbcDatabase.java`
- `desktop/src/main/java/fr/jarvis/services/platform/PdfBoxText.java`
- `desktop/src/test/java/fr/jarvis/services/BundledStorageTest.java`
- `desktop/src/test/java/fr/jarvis/services/FoundationTest.java`
- `desktop/src/test/java/fr/jarvis/services/ModulesTest.java`
- `docs/ARCHITECTURE.md`
- `docs/BUILD_ANDROID.md`
- `docs/CONTRACT_AUDIT.md`
- `docs/DEPENDENCIES.md`
- `docs/SECURITY.md`
- `docs/STORAGE.md`
- `docs/validation/mission-001.json`
- `gradle.properties`
- `gradle/verification-metadata.xml`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `scripts/cloud-gradle.sh`
- `settings.gradle`
- `shared-runtime/build.gradle`
- `shared-runtime/src/main/java/fr/jarvis/services/adapters/core/CoreAdapter.java`
- `shared-runtime/src/main/java/fr/jarvis/services/adapters/llm/LlmPort.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/actions/Actions.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/analyst/Analyst.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/bridge/Bridge.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/data/Data.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/documents/Documents.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/guard/Guard.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/knowledge/Knowledge.java`
- `shared-runtime/src/main/java/fr/jarvis/services/modules/memory/Memory.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/api/Contracts.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/api/Runtime.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/diagnostics/Diagnostics.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/storage/Database.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/storage/Recovery.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/storage/Store.java`
- `shared-runtime/src/main/java/fr/jarvis/services/shared/tasks/Task.java`
