# Reproduire FUSION-02 SERVICES

Base f00091a ; branche feat/fusion-02-services. JDK21, Gradle8.13 du wrapper,
SDK Google24/35, build-tools35.0.0. Garder la vérification des dépendances et TLS.
Ne pas modifier HOME. `scripts/cloud-gradle.sh` réutilise le proxy/CA de session.
Aucune commande ci-dessous ne doit viser un appareil personnel.

## Régressions et compilation

Dans le dépôt, avec JAVA_HOME/ANDROID_HOME/ANDROID_USER_HOME/GRADLE_USER_HOME
pointant vers des répertoires d'outils accessibles :

```sh
scripts/cloud-gradle.sh -PwithAndroid :desktop:test \
  :services-android-adapter:testDebugUnitTest \
  :services-android-adapter:assembleDebug \
  :services-android-adapter:assembleAndroidTest \
  :services-android-adapter:lintDebug \
  :services-saf-fixture:assembleDebug :services-saf-fixture:lintDebug \
  :android:assembleDebug :android:assembleAndroidTest :android:lintDebug \
  --no-daemon --max-workers=2
python3 scripts/check-services-contract-lock.py
python3 scripts/check-api24-compile.py --sdk "$ANDROID_HOME" \
  --dependency-cache "$GRADLE_USER_HOME/caches/modules-2/files-2.1" \
  --javac "$JAVA_HOME/bin/javac" --output build/fusion02-api24
python3 scripts/check-fusion02-evidence.py
```

Référence FINAL17 : créer une archive immuable de d16dd84 avec `git archive`,
l'extraire dans un répertoire temporaire et y lancer `:desktop:test`. Ce contrôle
séparé ne prouve aucune intégration des129 tests dans le port. Ne pas changer la
branche de référence ni modifier un autre dépôt.

## Vraie instrumentation isolée

Images officielles Google default/x86 API24r8 et25r1, API26r1 ; emulator37.3.4
build16555280. Checksums d'archives et empreintes enregistrés. API35 CI x86_64.
Une image doit être obtenue avec sdkmanager ou archive Google vérifiée, avec sa
licence acceptée par le mécanisme SDK habituel ; jamais de contournement TLS.
Cloud local sans KVM : `ANDROID_I_WANT_MY_TCG=yes`, option `-accel off`.
Créer un AVD synthétique neuf (1536Mio RAM,480x800), port console5580/ADB5581,
`-no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect`.
Les données d'AVD de cette mission sont exclusivement synthétiques.

Le runner utilise adb-shell0.4.4 dans un répertoire temporaire, pas un serveur
ADB ni de découverte. Exemple API24 (répéter séparément API25/26 avec l'AVD exact) :

```sh
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/run-fusion02-android.py \
  --api 24 --expected 15 \
  --fixture services-saf-fixture/build/outputs/apk/debug/services-saf-fixture-debug.apk \
  --apk services-android-adapter/build/outputs/apk/androidTest/debug/services-android-adapter-debug-androidTest.apk \
  --output /tmp/fusion02-evidence/android24-final.log
```

Le runner vérifie qemu/API/boot, prend un snapshot des deux APK pour figer les
SHA, installe avec `-t` et efface seulement les données **des deux APK de test**.
DocumentsUI, accords de grants, décision synthétique et export s'exécutent dans
les tests. `OK(15 tests)` et code terminal-1 sont nécessaires, avec15 cas à code0.
Un code terminal0 de crash, un compte incomplet ou un échec ne valide rien.
`--class-name package.Classe#methode --expected 1` est un diagnostic isolé.

Ne pas exécuter un second client UiAutomation/uiautomator pendant la campagne :
il concurrence le service d'accessibilité utilisé par les tests. Les timings
intègrent installation/TCG, pas une performance matérielle. Après une interruption
longue, inspecter la readiness ; cold boot peut aider mais ne remplace pas les
logs et assertions. Les événements de test doivent avoir un horodatage monotone
valide ; ne pas transformer un échec de sélection en grant fabriqué.

Arrêter seulement le processus qemu créé pour cet AVD, après vérification de son
binaire, nom/port et PID courant. Ne jamais réutiliser aveuglément un ancien PID.
Les comptes rendus de fermeture sont enregistrés séparément.

## Gate consommateur et reprise

- Une requête connue, un journal plein/corrompu ou un reçu UNKNOWN ne sont jamais
  réinitialisés pour rendre un test vert. Reprendre un workflow nécessite une
  décision utilisateur explicite ; aucune répétition de mutation incertaine.
- Le mode de panne et la révocation du provider de fixture sont commandés par un
  receiver à permission signature. Aucun grant MANAGE_DOCUMENTS n'est accordé au
  consommateur. Ce dispositif ne doit pas exister en production.
- `services-saf-fixture:installDebug` en CI est explicitement testOnly/-t ; ne pas
  distribuer cet APK. Le workflow conserve les trois jobs historiques et ajoute
  quatre jobs de ports distincts avec leurs résultats/artefacts.
- Le rapport PARTIAL ne devient pas GO après un simple succès de build. Une revue
  CORE/UNIFIED et la qualification de l'intégration consommatrice restent requises.


Deux AVD peuvent être qualifiés simultanément si CPU/RAM le permettent : console5580
→ `--port 5581`, console5582 → `--port 5583`. Le runner refuse les autres ports et
vérifie toujours le guest qemu/API exacte. Ce paramètre ne découvre aucun appareil.

En CI, `run-fusion02-ci.sh` exécute le contrôle de readiness OS puis Gradle, capture
l'interface finale avant l'arrêt de l'émulateur et retourne **le vrai code Gradle**.
Le contrôle de readiness est réservé à GITHUB_ACTIONS et emulator-5554, vérifie
qemu/API/boot, enregistre XML/PNG et refuse un ANR persistant ou d'une autre app.
Un unique appui Wait peut être enregistré sur un ANR System UI de démarrage.
Le mode capture-only ne clique rien et ne détermine pas le résultat des tests.
Aucune désactivation d'ANR, de garde, de quota ou de permission n'est autorisée.

Les preuves publiées expurgent uniquement hostname et configuration proxy héritée.
Les originaux exacts restent dans /tmp/fusion02-evidence/publication-originals ;
les hashes avant/après et règles sont dans evidence/publication-normalization.json.
Les compteurs/assertions et empreintes APK restent inchangés.
