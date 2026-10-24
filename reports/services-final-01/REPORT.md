# JARVIS — SERVICES-FINAL-01

Branche : **`feat/services-final-01`**. Base exacte :
`3decdc47d82f9b43713c48cab244762941770a81` (`feat/serv-03-services`).

Code applicatif : `a3372a4fd5e6239190257f423a73df7017752e29`.
Code et pilote Android final : `edfa044321ff09697ae9bec40a6e31aa76a77370`.
Le dernier commit de livraison des preuves/documentation est identifié dans
l’historique et la réponse de livraison; il ne change pas l’application.

**Résultats locaux finaux : 129 tests JVM réussis, dont les 92 cas SERV-03
conservés; 17 tests Android réussis sur émulateur API26 x86, dont trois parcours
avec le véritable Storage Access Framework. Aucun test ignoré.**
Les builds APK et AndroidTest ainsi que Lint réussissent : zéro erreur, un
avertissement tiers conservé. Un force-stop réel entre deux étapes est couvert
séparément des tests JUnit. Voir les résultats structurés dans
[validation.json](validation.json).

## Livrables et périmètre

- `b71bffdaacbbabec4200ee57e4c59644c191941b` : services, jobs durables, catalogue,
  protocole authentifié, contrats et tests JVM.
- `a3372a4fd5e6239190257f423a73df7017752e29` : interface autonome, confirmations,
  quotas, calculs et qualification SAF; le transport reste désactivé.
- `edfa044321ff09697ae9bec40a6e31aa76a77370` : rafraîchissement/défilement du pilote
  de test pour les listes SAF qui grandissent après plusieurs campagnes.

[Inventaire des fichiers de code modifiés et SHA-256](source-inventory.json).
La livraison documentaire ajoute README, [guide utilisateur](../../docs/services-final-01/USER_GUIDE.md),
[exemples réels](../../examples/services-final-01/), ce rapport, les journaux et
le helper de preuve du redémarrage. Aucun autre dépôt modifié, aucune fusion
main et aucune installation sur appareil personnel. `main` reste à
`c9f94ad2fa219a5e09cda92fb451aca606ee1646` au contrôle de publication.

## Les huit domaines

| Domaine | Fonction réellement livrée et vérifiée |
| --- | --- |
| DOCUMENTS | PDF textuel paginé, DOCX/texte, passages ciblés avec provenance, création DOCX, modèles `{{champ}}` remplis dans un formulaire, remplacement littéral, copies et sorties séparées |
| KNOWLEDGE | Port de recherche optionnel; adaptateur vers le contrat lexical existant, contrôle de révision, passages vérifiés contre les fichiers sélectionnés; aucun nouveau corpus central |
| DATA | CSV/JSON, nettoyage NFC/espaces, dédoublonnage, filtre exact, tris croissants, regroupements/sommes, types et unités déclarés, aperçu positionnel avant/après |
| ACTIONS | 17 outils versionnés couvrant les huit domaines, approbations exactes, workflows réutilisant le moteur SERV-03, annulation coopérative, journal append-only borné |
| MEMORY | Consultation/écriture confirmée/export local, expiration, quotas de 256 entrées durables et 128 volatiles; aucune mémoire centrale recréée |
| ANALYST | Statistiques/sommes exactes, comparaisons à unités égales, incohérences de type sourcées, fraction exacte de moyenne; approximation DECIMAL128 indiquée, interprétation/hypothèse séparées |
| GUARD | Grants hôte, destinations privées contrôlées, refus de traversées/liens symboliques, quotas et journaux, empreintes, confirmations à usage unique, refus des répétitions implicites |
| BRIDGE | Codec autonome HMAC-SHA256, appairage fourni par le host, séquences persistantes, anti-rejeu/déduplication, ACK authentifiés, UNKNOWN après interruption; aucune commande distante exécutée |

Le diagnostic historique et ses fonctions restent accessibles. Les trois outils
SERV-01, les workflows/journaux SERV-03, les contrats vendoriés et le transport
`Disabled` sont préservés. Les ajouts s’appuient sur ces fondations.
La suite 92→129 et les anciens tests natifs contrôlent les régressions connues;
aucune absence universelle de régression n’est revendiquée.

CORE reste l’autorité de décision/permissions dans une future intégration.
L’interface locale n’émet que des autorisations de l’utilisateur pour son atelier.
La mémoire de l’atelier est distincte de la mémoire centrale et du diagnostic
historique. NEXUS/LLM/VOICE ne sont pas requis, leurs dépôts/catalogues ne sont pas
modifiés. Aucune écriture mémoire centrale ni exécution déclenchée par un texte de
modèle n’est possible dans ces nouveaux workflows.

Voir les [responsabilités et contrats détaillés](../../docs/services-final-01/CONTRACTS.md)
et [schémas/catalogue versionnés](../../contracts/services-final/v1/).
Le contrôle `python3 scripts/check-services-contract-lock.py` a réussi pour les
deux sources UNIFIED vendoriées, limitées au module desktop.

## Preuves des scénarios complets

| Scénario | Preuve réellement exécutée | Résultat |
| --- | --- | --- |
| 1. PDF → information → synthèse sourcée → DOCX | JVM avec fichiers PDF/DOCX et trois parcours SAF, dont `pdfToSourcedDocxReadsBothActualSafInputAndOutput` | Texte/page/empreinte relus; original inchangé |
| 2. CSV → nettoyage → calcul → aperçu → export | JVM et `csvUsesActualPickerGrantPreviewConfirmationAndFreshTreeExport` | Deux lignes, somme exacte `0.30`, sortie CSV relue, original inchangé |
| 3. Interruption et reprise | Deux JVM distinctes puis force-stop du processus Android entre vérification et publication | Même job/étapes/journal après arrêt; vérification exécutée une fois; DOCX relu après confirmation |
| 4. Refus d’action | Appel sans grant, appel sans confirmation, journal saturé, chemins dangereux et preuves de refus | Aucun fichier créé; refus explicites, métadonnées sans arguments privés |
| 5. BRIDGE interrompu/doublonné/ACK | Fichiers de trame et de reçu réels entre deux participants JVM; tests authentification, reprise et commande interdite | UNKNOWN puis ACKED, une entrée d’inbox, zéro action distante |

Preuves : [scenarios.json](scenarios.json), [démonstration](final-demo.log),
[redémarrage Android](android-restart.json), [sortie du probe](android-restart-probe-final.log),
[campagne native finale](android-scroll-fixed.json), [sortie Android brute](android-scroll-fixed.log),
[129 cas JVM](jvm-final.json). Les trois tests SAF passent par Android DocumentsUI
et ses véritables grants URI : le provider contient uniquement des fixtures dans
l’APK de test, protégé par `MANAGE_DOCUMENTS`. Il n’est pas un provider de production.
Les sorties sont relues via ContentResolver avec le grant du dossier choisi.

Le formulaire de modèle est aussi qualifié avec `template.docx` réel : saisie
`Alice & Bob`/`0.30 EUR`, export DOCX et relecture, original vérifié inchangé.
Les corpus sont synthétiques; les fichiers, systèmes SQLite, processus, sélecteurs
et flux d’octets utilisés sont réels. Aucune donnée personnelle n’a été nécessaire.
Le BRIDGE utilise une paire et une clé de qualification synthétiques; il ne
constitue pas une connexion à un mini-PC utilisateur.

Le scénario Android utilise un force-stop réel **entre étapes**. Il ne constitue
pas une coupure électrique au milieu d’une écriture. Les états en cours/UNKNOWN,
corruptions, pertes d’ACK et rollbacks sont vérifiés séparément par tests/injections
contrôlées. Une demande de confirmation de fixture n’est pas une autorisation
réutilisable pour une opération utilisateur.

## Commandes, builds et environnement

Instance cloud reconstruite avec JDK complet Oracle21.0.8 vérifié auprès de son
éditeur, Gradle8.13 épinglé/vérifié, AGP8.9.2, sources Java11, SDK35 base r02,
build-tools35.0.0. Émulateur officiel37.3.4, image API26 x86, rendu logiciel sans
KVM. Provenances : [JDK](jdk-provenance.json), [SDK](sdk-provenance.json),
[émulateur/image](emulator-provenance.json), [formatter](formatter-provenance.json).
Proxy, TLS et vérification Gradle sont conservés. Aucun changement de HOME,
authentification système, permissions matérielles ou protection système désactivée.

```sh
export JAVA_HOME=/tmp/jarvis-tools/jdk
export ANDROID_HOME=/tmp/jarvis-tools/android-sdk
export ANDROID_USER_HOME=/tmp/jarvis-tools/android-user
export GRADLE_USER_HOME=/tmp/jarvis-tools/cache
scripts/cloud-gradle.sh -PwithAndroid :desktop:test :desktop:finalScenariosDemo \
  :android:assembleDebug :android:assembleAndroidTest :android:lintDebug --no-daemon
python3 scripts/check-services-contract-lock.py
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/run-isolated-android.py \
  --app android/build/outputs/apk/debug/android-debug.apk \
  --tests android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk \
  --expected 17 --output reports/services-final-01/android-scroll-fixed.log
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/probe-final-restart.py
```

Les dépendances doivent avoir été téléchargées avant une exécution Gradle
`--offline`; cette option n’est pas assimilée à un test d’interdiction réseau.
Le runner Android refuse l’installation sans identité qemu et démarrage API26+
confirmés, sur la seule adresse de l’émulateur isolé.

[Journaux du build qualifié](release-candidate-build.log),
[build du pilote corrigé](saf-scroll-build.log). Résultats JVM : 129 réussites,
0 échec, 0 erreur, 0 ignoré; [comparaison des 92 noms d’origine](jvm-final.json).
Lint : 0 erreur, 1 avertissement `TrustAllX509TrustManager` dans BouncyCastle1.86,
identique au tiers déjà analysé. Aucun avertissement applicatif restant ni
suppression/baisse de sévérité ([preuve](lint-final.json)). Les dépréciations
Gradle/JDK déjà présentes restent visibles dans les journaux.

## Échecs intermédiaires conservés

- Premier lancement JVM : JRE sans `javac` ([journal](baseline.log)); JDK complet
  installé et 92 tests ensuite réussis ([baseline](baseline-jdk-fixed.log)).
- Première préparation SDK : variante d’extension35 sélectionnée au lieu de la
  plateforme base; message d’installation/licence ([journal](workspace-build.log)).
  Correction par installation de l’archive officielle base vérifiée, sans modifier
  les contrôles de licence/TLS ([succès](android-sdk-fixed.log)).
- Nouveau test de l’adaptateur KNOWLEDGE : NPE sur `Map.of().containsValue(null)`;
  validation corrigée et test conservé ([échec](jvm-adapter-failure.xml)).
- Deux essais du pilote SAF ont échoué sur la casse des boutons puis l’absence
  d’action d’accessibilité des lignes : [premier](saf-first.json),
  [second](saf-case-fixed.json). Le clic tactile réel a permis deux réussites
  ([résultat](saf-touch-fixed.json)), puis 16 et 17 tests natifs réussis
  ([16](android-full.json), [17](android-final.json)).
- Une répétition du candidat a ensuite donné 15 réussites/2 échecs dans le pilote
  lorsque les exports accumulés poussaient les entrées hors écran
  ([essai](android-release-candidate.json)). `edfa044` rafraîchit les nœuds après
  défilement et stabilise l’ordre des fixtures. La campagne finale corrigée
  réussit 17/17 sans effacer les anciennes preuves.

- Le probe externe de reprise a d’abord copié une base SQLite vivante et obtenu
  une copie incohérente ([échec](android-restart-live-copy-failure.json)). Les
  contrôles ultérieurs de la base d’origine et de sa copie à l’arrêt donnent
  `integrity_check=ok` ([diagnostic](restart-db-diagnosis.json)). Le helper ne lit
  désormais que des snapshots à l’arrêt avec leurs éventuels journaux, et vérifie
  l’état repris dans l’interface Android native avec une hiérarchie fraîche.
- L’inspection de l’aperçu après redémarrage était aussi masquée par le clavier
  ([essai](android-restart-viewport-failure.json)). Le helper ferme le clavier par
  l’action Retour normale et défile dans le viewport réel. Les tentatives restent
  distinctes de la preuve finale réussie. Aucun correctif de base SQLite ou
  modification de l’APK n’a été nécessaire pour ces erreurs d’observation.

## APK, manifeste et CI

APK local réellement qualifié : `android/build/outputs/apk/debug/android-debug.apk`.
Taille : **14 423 733 octets**. SHA-256 :
`3c8b19a8c6ba5b84eee56bed3a8481568345c09f757876c247b585152effc6f9`.

Le [manifeste d’artefacts](apk-manifest.json) contient aussi taille/empreinte de
l’APK de test et le contexte de capture. L’application est inchangée depuis
`a3372a4`; `edfa044` corrige uniquement deux fichiers de test. Les modifications
locales de documentation/helper au moment de la capture sont signalées, et les
empreintes sont comparées à celles réellement installées par le runner.
Signature debug vérifiée ([preuve](apk-signature.log)). Ce n’est pas une signature
ni une livraison de production. Le dump du manifeste ne demande **aucune permission**,
notamment pas INTERNET ([dump](apk-permissions.log), [packaging](packaging.json)).
Les ABI embarquées ne constituent pas une qualification sur matériel ARM.

Le workflow inclut la branche et conserve les jobs JVM, build/Lint et instrumentation
séparés, avec artefacts APK/manifeste/résultats. Run du pilote corrigé :
[40142362407](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40142362407).
**Conclusion distante observée : SUCCESS, trois jobs réussis, durée 7 min 12 s**
([ci.json](ci.json)), relevée sur les pages publiques GitHub. Un succès local n’est pas assimilé à un succès distant. Le premier run
`a3372a4` est supersédé par le correctif du pilote. Le commit final de preuves peut
déclencher un nouveau run documentaire; les résultats rapportés concernent le
commit de code/pilote identifié ci-dessus.

## Ressources, limites et suites

Benchmarks réellement exécutés sur fichiers : CSV100/1000/5000 lignes
(jusqu’à 1 040 019 octets) et PDF1/10/50 pages. Les sorties CSV ont été relues;
les nombres de pages PDF sont contrôlés. [Mesures](benchmarks.json) : une passe
par fichier sur JVM Linux, incluant lecture et vérification; aucune promesse de
latence statistique ou sur appareil Android. [Ressources natives](resources.json) :
8 points PSS après scénarios et VmHWM cumulatif; ces mesures ne sont pas des pics
d’allocation par opération. Aucune mesure batterie/thermique physique.

Limites explicites : API26 x86 émulée qualifiée, appareils physiques et autres
versions Android non qualifiés; OCR/XLSX/ZIM, mise en page DOCX arbitraire,
modification PDF en place et synchronisation réelle non livrés. Les sources sont
bornées à 2MiB/100pages, les résultats à 250000 caractères, les tables à
5000lignes/100colonnes. CSV/JSON restent les formats tabulaires qualifiés.
Les journaux/jobs nouveaux ne sont pas inclus dans le snapshot historique Recovery;
une migration/sauvegarde globale n’est pas revendiquée. UNKNOWN et saturation
nécessitent une vérification ou nouvelle opération explicite; aucune purge ou
relance automatique ne masque l’incertitude.

Prochaines étapes : qualification ARM64 sur appareil autorisé, mesures matérielles
et éventuelle extension des limites; intégrations CORE/NEXUS/LLM uniquement avec
contrats/autorisation appropriés. L’appairage et les transports matériels BRIDGE
restent une mission distincte. Aucune installation utilisateur, activation réseau
ou fusion dans main n’est effectuée par cette livraison.
