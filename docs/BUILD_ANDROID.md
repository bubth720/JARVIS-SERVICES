# Construction et diagnostic Android

JDK 21 et Gradle wrapper 8.13 ; SDK platform Android 35, build-tools 35.0.0,
AGP 8.9.2. minSdk 26 propre à ce nouveau diagnostic, targetSdk 35 ; aucun changement
aux SDK des autres applications. Définir ANDROID_HOME vers le SDK et disposer
d'un répertoire ANDROID_USER_HOME accessible en écriture. Les packages SDK doivent
provenir de Google avec intégrité vérifiée ; respecter leurs licences.

```sh
./gradlew :desktop:test
./gradlew -PwithAndroid :android:assembleDebug :android:assembleAndroidTest :android:lintDebug
# Cache déjà préparé :
./gradlew --offline -PwithAndroid :desktop:test :android:assembleDebug
```

Dans ce Cloud, le JRE initial ne contenait pas javac. Un JDK 21 a été téléchargé
avec SHA-256 officiel vérifié, ainsi que Gradle 8.13. Les archives SDK platform et
build-tools ont été vérifiées avec les empreintes du manifeste Google HTTPS.
Les chemins de travail de cette session :

```sh
export JAVA_HOME=/tmp/jarvis-tools/jdk-21.0.12.1
export GRADLE_USER_HOME=/tmp/jarvis-tools/cache
export ANDROID_HOME=/tmp/jarvis-tools/android-sdk
export ANDROID_USER_HOME=/tmp/jarvis-tools/android-user
scripts/cloud-gradle.sh -PwithAndroid :desktop:test :android:assembleDebug :android:assembleAndroidTest :android:lintDebug
```

Ces chemins /tmp ne sont pas une garantie de conservation dans une nouvelle tâche.
Le script ne télécharge pas le SDK et ne change pas les protections réseau : il
transmet le proxy hérité à Java et utilise le magasin de certificats système.
Ne pas désactiver TLS ou écraser un truststore personnalisé.

L'APK debug doit être installé UNIQUEMENT après autorisation sur appareil utilisateur.
Pour l'instrumentation, utiliser un émulateur de test déjà autorisé :

```sh
./gradlew -PwithAndroid :android:connectedDebugAndroidTest
```

État historique Mission001 : trois tests compilés mais non exécutés, sans émulateur
préinstallé. En 002A, un émulateur API26 logiciel isolé a été préparé et les mêmes
cas ont réellement été exécutés. Voir [la procédure et ses limites](002A_QUALIFICATION.md)
et [les preuves 002A](../reports/002a/REPORT.md). Cela ne qualifie ni un téléphone
physique, ni les autres versions Android, ni le raccordement CORE/LLM.

Le diagnostic démarre sans modèle et sans calcul lourd. Toute interaction lourde
passe par un worker unique, avec admission RAM/batterie/température et annulation
en arrière-plan. Les fichiers restent dans files/services et les imports sont
copiés depuis SAF. Le bouton rapport exporte capacités/version/audit sans texte privé.
Le DOCX créé par workflow reste privé dans files/services/documents ; il n'existe
pas encore de sélecteur de partage de ces sorties. Pas de service d'arrière-plan.

Une application Android assemblée n'est pas une qualification de fonctionnement
sur POCO X8 Pro ou Redmi Pad SE. Les tests physiques, la sobriété réelle, les
parseurs malformés et l'interopérabilité CORE/LLM devront être qualifiés séparément.
