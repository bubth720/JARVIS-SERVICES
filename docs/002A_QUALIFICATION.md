# Reproduire la qualification 002A

Utiliser le checkout existant et la branche de mission, sans worktree ni autre dépôt.
JDK21, Gradle8.13 et SDK35/build-tools35.0.0 restent requis. Les contrats restent v1.

## JVM, APK et Lint

```sh
scripts/cloud-gradle.sh --offline -PwithAndroid :desktop:test :android:assembleDebug :android:assembleAndroidTest :android:lintDebug --rerun-tasks --no-daemon
python3 scripts/write-apk-manifest.py --output android/build/outputs/apk/manifest.json
```

Définir les chemins JAVA_HOME, GRADLE_USER_HOME, ANDROID_HOME et ANDROID_USER_HOME
comme dans BUILD_ANDROID.md. Omettre `--offline` lors de la préparation initiale du
cache de dépendances. Ne jamais désactiver TLS ou la vérification Gradle.

## Émulateur isolé

Packages Google : `emulator`, `platform-tools`, `system-images;android-26;default;x86`.
Archives et empreintes effectivement vérifiées : `reports/002a/sdk-provenance.json`.
Le profil réel est dans `reports/002a/emulator-config.ini` : 1536 Mio, deux CPU,
480x800, image AOSP sans Play Store. Créer un AVD jetable dédié avec ce profil,
avec ANDROID_AVD_HOME dans un répertoire temporaire neuf. Ne pas réutiliser un AVD
personnel. La session de qualification a utilisé `/tmp/jarvis-tools/avd-002a`.

Commande utilisée après création de l'AVD :

```sh
ANDROID_I_WANT_MY_TCG=yes ANDROID_USER_HOME=/tmp/jarvis-tools/android-user ANDROID_AVD_HOME=/tmp/jarvis-tools/avd-002a ANDROID_HOME=/tmp/jarvis-tools/android-sdk /tmp/jarvis-tools/android-sdk/emulator/emulator -avd jarvis-002a-api26 -port 5580 -accel off -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -no-metrics
```

Emulator37 demande explicitement ANDROID_I_WANT_MY_TCG pour sélectionner son
interpréteur logiciel. Cela ne donne aucun droit KVM et ne modifie aucune
protection OS, thermique, batterie ou RAM. Aucune permission /dev/kvm n'a été
accordée. La console n'a pas pu écrire son fichier d'authentification dans le home
non inscriptible ; elle n'a pas été utilisée et aucune authentification n'a été
désactivée. Les ports ADB de cet AVD restent locaux à la machine de test.

Le SDK adb37 utilise un home non inscriptible dans ce Cloud, même avec les variables
Android. Aucun HOME système n'a été réaffecté. Le client alternatif adb-shell0.4.4
utilise le protocole ADB standard vers le seul port local5581 et refuse toute
installation si `ro.kernel.qemu != 1`, SDK<26 ou boot incomplet. Il n'effectue
aucune découverte d'appareils et ne peut sélectionner un téléphone personnel.
Le serveur adbd de l'image AOSP de test accepte ce transport ; aucune configuration
d'authentification Android n'a été modifiée.

```sh
python3 -m pip install --target /tmp/jarvis-tools/adb-python --cache-dir /tmp/jarvis-tools/pip-cache adb-shell==0.4.4
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/run-isolated-android.py --app android/build/outputs/apk/debug/android-debug.apk --tests android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk --expected 11 --output reports/002a/instrumentation-final.log
```

Le script préserve les résultats JUnit réels, exige le nombre de cas attendu,
un résultat terminal et zéro échec/saut. Un résultat zéro-test n'est pas accepté.
Il enregistre SHA-256/taille des APK, identité de l'émulateur, commandes et durées.
Il faut arrêter uniquement l'émulateur créé pour la tâche après collecte des preuves.

Sur un runner disposant du SDK adb et d'un AVD de test exclusivement :

```sh
./gradlew -PwithAndroid :android:connectedDebugAndroidTest
```

Cette dernière commande est enregistrée en CI ; elle n'a pas été exécutée localement
avec adb37, à cause du home non inscriptible. Localement, les mêmes APK et le même
AndroidJUnitRunner ont été exécutés réellement par ADB direct. Aucun test JVM ou
Robolectric n'est présenté comme une exécution Android.

## Données et mesures

Les fixtures PDF/CSV/mémoire sont synthétiques. Le provider de test n'existe que
dans l'APK AndroidTest et ne sert que sample.pdf/report.json, sans chemin arbitraire.
Les tests exercent ContentResolver et le code de production d'import/export SAF ;
ils ne pilotent pas l'interface du sélecteur de documents du système.

Les huit scénarios complémentaires envoient durée monotone, PSS de fin de scénario
et VmHWM du processus via Instrumentation. VmHWM est le pic RSS cumulé jusqu'au
checkpoint, pas une mesure isolée d'allocation de chaque fonction. Aucun relevé
n'est une mesure de consommation énergétique. Le cas mémoire ferme/réouvre SQLite
et reconstruit Runtime ; il ne simule pas un redémarrage du téléphone.

La CI sépare régressions JVM, APK, Lint et instrumentation sur API26. Ses actions
sont fixées à des commits vérifiés. Le fallback logiciel est permis sans changer
les protections système. Un workflow livré n'est pas une CI verte ; les observations
distantes et les blocages d'accès sont consignés dans reports/002a/validation.json.

## Persistance après arrêt du processus

```sh
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/probe-emulator-memory.py
```

Sonde Cloud réservée au même AVD local5581 vérifié qemu : elle confirme une préférence
synthétique exacte par l'UI, attend le reçu, force l'arrêt de cette seule application,
la relance, vérifie le changement de PID et lit le résultat persistant. Elle ne
contourne pas la confirmation et n'est pas comptée parmi les onze cas JUnit. Les
premières tentatives TCG incomplètes (ANR SystemUI et saisie tronquée, refusée) restent
publiées ; la dernière a réussi. Aucun redémarrage matériel ni fichier personnel.
