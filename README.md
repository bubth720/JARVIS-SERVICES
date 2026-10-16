# JARVIS SERVICES — Foundation V1

Huit bibliothèques locales et une application Android de diagnostic. Branche de travail :
`mission-001-services-foundation`. Aucun service cloud, télémétrie ou téléchargement de
modèle au runtime. Ce dépôt ne modifie ni CORE ni LLM.

## Construire et tester

Prérequis : JDK 21 (testé), accès aux dépôts Maven pour la première compilation.
Le wrapper fixe Gradle 8.13 et vérifie son SHA-256.

```sh
./gradlew :desktop:test
# Après installation du SDK Android 35 et des build-tools 35.0.0 :
./gradlew -PwithAndroid :android:assembleDebug :android:assembleAndroidTest :android:lintDebug
# Sur un émulateur de test déjà démarré, jamais sur un appareil utilisateur sans accord :
./gradlew -PwithAndroid :android:connectedDebugAndroidTest
```

Les artefacts sont dans `android/build/outputs/apk/debug/android-debug.apk` et
`android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk`.
Les résultats JVM sont dans `desktop/build/reports/tests/test/`.
`--offline` réutilise les dépendances déjà téléchargées ; il ne remplace pas les tests
qui interdisent réellement les sockets au runtime.

Dans le Cloud à proxy, voir [le guide de construction](docs/BUILD_ANDROID.md).
Utiliser le checkout existant, sans créer de worktree.

## Bibliothèques

- `shared-runtime` : contrats, stockage abstrait, sécurité, tâches et huit modules.
- `desktop` : SQLite JDBC, PDFBox JVM et tests (dont le code exact de l'adaptateur SQL Android sur bibliothèque native JVM).
- `android` : SQLite AndroidX embarqué avec FTS5, PDFBox Android, SAF, diagnostic.
- `contracts/v1` : schémas SERVICES internes ; aucune déclaration de compatibilité IPC UNIFIED.

L'application affiche les capacités et limites, importe les documents via SAF,
recherche (texte/FTS5), produit un résumé extractif et un rapport multi-documents avec
LLM explicitement simulé, dédoublonne un CSV, confirme une préférence ou une création
DOCX, exécute le simulateur BRIDGE et exporte un rapport technique sans documents.

## Portée vérifiée et limites

Consulter [MISSION_001_REPORT.md](MISSION_001_REPORT.md) pour les preuves réelles,
les SHA, les tests non exécutés et les étapes restantes. La recherche vectorielle
utilise un petit dictionnaire FR/EN et du feature hashing : ce n'est pas un modèle
sémantique neuronal. OCR, XLSX, pilote ZIM, matériel BRIDGE et connexion réelle
CORE/LLM restent indisponibles. Aucun modèle LLM n'est intégré.

[Architecture et contrats](docs/ARCHITECTURE.md) · [Sécurité](docs/SECURITY.md) ·
[Stockage/récupération](docs/STORAGE.md) · [Audit des contrats connexes](docs/CONTRACT_AUDIT.md) ·
[Dépendances](docs/DEPENDENCIES.md)

Qualification 002A : [rapport et preuves](reports/002a/REPORT.md), 59 tests JVM et 11 tests instrumentés exécutés sur émulateur API26 x86 ; matériel physique et résultat CI distant non qualifiés.
