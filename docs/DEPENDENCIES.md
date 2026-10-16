# Dépendances et licences tierces

Les versions directes sont fixées dans les scripts Gradle ; les empreintes SHA-256 des artefacts résolus sont conservées dans `gradle/verification-metadata.xml` et vérifiées par Gradle. Ce sont des empreintes initialement observées via HTTPS de confiance, pas une vérification de signatures éditeur ; le wrapper vérifie le
SHA-256 de Gradle. TLS est conservé. Aucune dépendance ou poids de modèle n'est
récupéré au runtime applicatif. Aucun moteur LLM n'est inclus.

| Dépendance | Version | Rôle | Licence amont |
|---|---|---|---|
| Gson | 2.11.0 | Sérialisation locale Java/Android | Apache-2.0 |
| sqlite-jdbc | 3.46.1.0 | Adaptateur SQLite JVM | Apache-2.0 ; SQLite domaine public |
| PDFBox JVM | 2.0.33 | PDF textuel JVM | Apache-2.0 |
| PDFBox Android | 2.0.27.0 | PDF textuel APK | Apache-2.0 |
| Bouncy Castle bcpkix/bcprov/bcutil jdk15to18 | 1.86 | Helpers PDF Android alignés explicitement | MIT |
| AndroidX SQLite bundled | 2.5.0 | SQLite embarqué et FTS5 Android/JVM | Apache-2.0 ; SQLite domaine public |
| JUnit Jupiter | 5.11.4 | Tests JVM | EPL-2.0 |
| AndroidX test runner / ext junit | 1.6.2 / 1.2.1 | Instrumentation | Apache-2.0 |
| Android Gradle Plugin | 8.9.2 | Construction APK | Apache-2.0 |
| Gradle | 8.13 | Construction | Apache-2.0 |

Les transitives comprennent notamment FontBox/Commons Logging (Apache-2.0),
Kotlin stdlib/annotations AndroidX (Apache-2.0), SLF4J (MIT), JUnit4 (EPL-1.0),
Hamcrest (BSD-3-Clause) et OpenTest4J (Apache-2.0). Cette liste n'est pas un audit
juridique exhaustif ni un scan de vulnérabilités. Avant diffusion publique :
produire SBOM/NOTICES complets de l'APK final et vérifier les avis de sécurité,
en particulier le fork PDFBox Android plus ancien. Pas de licence du projet
SERVICES inventée par cette mission.

Inventaire reproductible :

```sh
./gradlew :desktop:dependencies --configuration runtimeClasspath
./gradlew -PwithAndroid :android:dependencies --configuration debugRuntimeClasspath
```

SDK Android : installé dans l'environnement de build, jamais dans l'APK ; ses
conditions de licence s'appliquent séparément. Aucun modèle/jeu de données externe
n'est nécessaire aux tests livrés. Les PDF et données de test sont synthétiques.
