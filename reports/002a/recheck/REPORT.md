# Mission 002A — reprise de qualification et correctifs CI

Branche exclusive : `mission-002a-android-qualification`.
Départ : `34ab1c2147c439b205b9a49383f3b4f0c6368d43`.
Correctif CI et intégrité : `c2c129850143aa4f8187666dc5dea4520a0843ab`.
Le commit suivant de preuves est identifiable dans l'historique Git. Cette reprise
ne change aucun fichier applicatif des huit modules, aucun contrat ni dépendance
applicative. Aucun autre dépôt modifié, aucun merge, aucun appareil personnel utilisé.

## Diagnostic et correctifs

Le run précédent [38917701595](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/38917701595)
a réellement échoué : étape JVM et `setup-android` dans les deux jobs Android
([étapes observées](remote-failed-steps.json)). L'API est refusée par le proxy
HTTP 403 et les endpoints de logs web renvoient 404 sans authentification.
Les pages publiques restent accessibles : les états distants ci-dessous sont des
observations GitHub, pas des simulations. Les messages détaillés de l'ancien run
ne sont pas disponibles; la correspondance exacte avec les causes reproduites
localement ne peut donc pas être affirmée à partir des anciens logs.

Deux échecs ont été reproduits avec un nouveau cache Gradle, alors que le cache
initial permettait le build :

- JVM : empreinte absente pour `junit-bom-5.11.4.pom` ([échec](clean-jvm.log)).
- Configuration Android : empreinte absente pour `junit-bom-5.10.2.module`
  ([échec](clean-android.log)).

Les deux seuls SHA-256 manquants ont été ajoutés à
`gradle/verification-metadata.xml`, après comparaison du téléchargement utilisé
par Gradle avec Maven Central et ses fichiers `.sha256` :
[preuve POM](junit-pom-provenance.json), [preuve module](junit-module-provenance.json).
Aucune empreinte existante remplacée, aucune vérification désactivée, aucun
contournement TLS ni mode permissif.

Le workflow `.github/workflows/verify.yml` conserve trois jobs distincts et :

- utilise `setup-android` v4 et `packages: platform-tools` explicitement; la v3
  demandait aussi `tools`, désormais absent du catalogue Google
  ([catalogue](android-sdk-catalog.json));
- fixe checkout v5, setup-java v5, upload-artifact v6 et setup-android v4 sur des
  SHA immuables; les cinq manifests d'actions déclarent Node.js 24
  ([sources et empreintes](action-pins.json));
- fixe Ubuntu 24.04, évitant la migration annoncée de `ubuntu-latest` vers Ubuntu 26;
- conserve les rapports de vérification Gradle en artefacts en cas d'échec JVM/build.

## Commandes et résultats locaux

Les commandes utilisent les outils déjà préparés : JDK 21.0.12.1, Gradle 8.13,
AGP 8.9.2, compileSdk 35. `scripts/cloud-gradle.sh` transmet le proxy et le magasin
CA du cloud sans modifier HOME. Variables :

```sh
export JAVA_HOME=/tmp/jarvis-tools/jdk-21.0.12.1
export ANDROID_HOME=/tmp/jarvis-tools/android-sdk
export ANDROID_USER_HOME=/tmp/jarvis-tools/android-user
export GRADLE_USER_HOME=/tmp/jarvis-tools/cache
scripts/cloud-gradle.sh --offline -PwithAndroid :desktop:test \
  :android:assembleDebug :android:assembleAndroidTest :android:lintDebug \
  --rerun-tasks --no-daemon
```

Résultat : succès, 72 tâches exécutées, [journal](build.log).
59 tests JVM réussis, 0 échec, 0 erreur, 0 ignoré. Les noms des 55 cas Mission 001
ont été comparés aux résultats frais : tous présents et réussis
([cas JVM](jvm.json), [comparaison](regression-check.json)). Les quatre autres
cas restent les tests de sécurité DOCX ajoutés précédemment.

Un second cache `/tmp/jarvis-tools/recheck-clean-cache` a reçu seulement la
distribution Gradle déjà vérifiée, sans cache de dépendances. Les deux commandes
suivantes ont révélé les empreintes manquantes, puis ont été relancées après
correction, avec téléchargements réels et vérification stricte :

```sh
export GRADLE_USER_HOME=/tmp/jarvis-tools/recheck-clean-cache
scripts/cloud-gradle.sh :desktop:test --rerun-tasks --no-daemon
scripts/cloud-gradle.sh -PwithAndroid :android:assembleDebug \
  :android:assembleAndroidTest :android:lintDebug --rerun-tasks --no-daemon
```

Résultat JVM corrigé : 59 réussites, [journal](clean-jvm-fixed.log).
Résultat Android corrigé : succès, 69 tâches exécutées,
[journal](clean-android-fixed.log). Lint : 0 erreur, 1 avertissement
`TrustAllX509TrustManager` dans la dépendance tierce déjà analysée; aucun nouveau
avertissement applicatif et aucune réduction de sévérité ([preuve](lint.json)).

Un émulateur API 26 x86 a été démarré sans accélération matérielle, uniquement
sur l'AVD isolé `jarvis-002a-api26`, ports locaux 5580/5581. Après vérification
`ro.kernel.qemu=1`, API 26 et `sys.boot_completed=1` :

```sh
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/run-isolated-android.py \
  --app android/build/outputs/apk/debug/android-debug.apk \
  --tests android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk \
  --expected 11 --output reports/002a/recheck/instrumentation.log
```

Résultat : **11 réussites, 0 échec, 0 ignoré**, code terminal Android -1,
[sortie native](instrumentation.log), [cas et métriques](instrumentation.json).
Les trois cas d'origine passent : Activity, SQLite/FTS5 natif, absence de permission
INTERNET. Les autres couvrent PDF/FTS/DOCX, DTD UTF-8/UTF-16, CSV, mémoire confirmée,
GUARD/échec sûr, restauration isolée et BRIDGE simulé, import/export ContentResolver.
Ce sont des tests Android exécutés sur émulateur, avec données synthétiques;
BRIDGE reste simulé et aucun raccordement réel CORE/LLM/NEXUS n'est revendiqué.
L'AVD a ensuite été arrêté après vérification de son PID/identité
([arrêt](emulator-shutdown.json)).

Les deux APK reconstruits ont les mêmes SHA-256 que ceux effectivement installés
et testés : [manifeste](apk-manifest.json), [comparaison](regression-check.json).
Les APK debug ne sont ni des livraisons de production ni des installations utilisateur.

## CI distante, limites et prochaines étapes

Le run du correctif est [38921336822](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/38921336822).
**Conclusion observée : SUCCESS, durée totale 6 min 54 s. Les trois jobs JVM,
build/Lint Android et instrumentation Android ont réussi.** Les étapes détaillées
observées sont toutes `success`; les trois artefacts produits figurent dans
[le résumé public capturé](remote-summary.txt).

Son état réel et ceux des jobs sont enregistrés dans
[remote-runs.json](remote-runs.json) et [remote-jobs.json](remote-jobs.json).
Voir [validation.json](validation.json) pour l'état de clôture; une étape locale
réussie ne remplace pas la conclusion GitHub Actions.

Aucune régression observée dans les scénarios automatisés exécutés. Cette preuve
ne couvre pas tous les appareils, toutes les versions Android ou tous les cas
fonctionnels possibles. L'application reste inchangée, sans permission réseau
ajoutée; les limites Android et l'audit ADR-001 du rapport principal restent valables.

Restent non réalisés pendant cette reprise : tests sur téléphone/tablette/montre
physiques, mesures batterie/thermique sur matériel réel, nouveau probe UI de
force-stop/redémarrage (la preuve précédente reste conservée), manipulation du
sélecteur SAF système et synchronisation BRIDGE matérielle. Les métriques natives
présentes dans le JSON concernent uniquement cet émulateur; aucun gain énergétique
n'est déduit. L'intégration CORE/LLM demeure hors périmètre 002A.

Huit points de mesure : PSS maximal observé 27 146 KiB; maximum noyau VmHWM
86 456 KiB (cumulatif au processus). Durée hôte transfert/installation/tests :
159,332 s. Ces valeurs ne constituent pas un pic d’allocation par scénario ni une
mesure sur appareil physique ([interprétation et sources](resources.json)).

Aucun blocage restant pour ces correctifs : les trois jobs du commit de code
ont réussi à distance. Aucune action utilisateur nécessaire pour les correctifs
livrés. La validation sur matériel physique reste une étape ultérieure soumise
à autorisation. Aucune fusion dans main sans validation explicite.

Le commit de livraison des preuves ne modifie que `reports/002a/` et peut
déclencher un nouveau run documentaire. Le succès CI ci-dessus concerne
explicitement le commit de code `c2c1298`, sans assimiler ce nouveau run à un
résultat déjà observé.
