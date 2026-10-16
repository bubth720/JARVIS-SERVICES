# JARVIS SERVICES — Mission 002A

> Mise à jour : [reprise de qualification et correctifs CI](recheck/REPORT.md).
> Le correctif `c2c1298` résout les échecs de vérification observés avec un cache
> vierge et actualise la préparation Android. Les preuves de la campagne initiale
> ci-dessous sont conservées. Le run `38921336822` du correctif est réussi
> (trois jobs); les détails figurent dans le rapport de reprise.

Qualification Android et fiabilisation de FOUNDATION V1 — 16 octobre 2026.
Branche exclusive : `mission-002a-android-qualification`.

**Résultat : 59 tests JVM et 11 tests Android instrumentés réussis sur un émulateur
API26 x86. Les 55 cas JVM et trois cas Android d'origine sont conservés.**
La campagne Android corrigée a réussi deux fois. Cela ne qualifie pas les appareils
physiques ni les autres versions Android. Lors de cette campagne initiale, la CI
distante n’était pas observable via l’API; voir la reprise pour les observations
désormais obtenues via les pages web publiques.

## Références et périmètre

- Base FOUNDATION exacte : `075158b755000f8c6d8e37747cec9de8221daa72`.
- Départ de la branche avec le cahier des charges 002A : `da762b3e3204707b5a3a05ec7a4946ec1aad2d64`.
- SHA final du code qualifié : `767ac376e03011a79cf6061a5f088b77f478282d`.
- `6ff384b` : correctifs Android/DOCX et tests natifs ; `767ac37` : CI et outils.
- Le commit documentaire portant ce rapport est identifié dans l'historique et
  dans la réponse de livraison ; il ne change pas les sources des APK qualifiés.

Le cahier des charges et les preuves Mission001 ont été lus intégralement.
L'ascendance de la base a été vérifiée. Aucun autre dépôt JARVIS modifié, aucune
fusion main, aucune installation sur appareil personnel. Les seuls déploiements
ont ciblé l'AVD jetable, avec vérification qemu=1, API26 et boot terminé.

ADR-001 relue en lecture seule au SHA approuvé UNIFIED `e0e6f69c4908a152da4e43864a08fef0c8f7a7af`.
Voir [l'audit de compatibilité](../../docs/002A_COMPATIBILITY.md) et
[compatibility-audit.json](compatibility-audit.json). CORE conserve décisions,
permissions et confirmations ; NEXUS reste la cible future du stockage confirmé ;
le moteur et le catalogue LLM restent inchangés. Les données KNOWLEDGE/MEMORY de
SERVICES sont celles du diagnostic isolé, sans service concurrent ni migration.

BRIDGE n'a pas reçu de transport/synchronisation réelle. Le document de compatibilité
prévoit autonomie hors ligne PC/téléphone/montre, pairs autorisés par CORE, révisions,
accusés, déduplication et reprise, sans décider des conflits à la place de NEXUS.

## Défaut démontré et correctifs

La première campagne étendue a donné **9 succès et 1 échec réel** : le DOCX exporté
ne pouvait pas être réimporté sur Android26. Le diagnostic ciblé a démontré
`ParserConfigurationException` sur `disallow-doctype-decl` : la factory Harmony
Android ne supporte pas cette option propre à Xerces. Ce n'était pas un échec PDF.
Preuves : [campagne initiale](expanded-instrumentation-first.log), [diagnostic](docx-diagnosis.log).

Le lecteur DOCX utilise maintenant SAX, portable JVM/Android, sans ignorer une
protection non supportée. Le callback lexical DTD est obligatoire et son installation
échoue de façon sûre si indisponible ; toute DTD/entité externe est refusée, les erreurs
XML restent fatales et le texte extrait est borné. Tests nouveaux : XML UTF16 valide,
entités intégrées, DTD UTF8/UTF16 refusées, XML malformé et taille extraite. Les tests
natifs prouvent la réimportation DOCX et le refus des DTD encodées.

Les textes de statut affichés sont déplacés dans strings.xml. Les règles de
sauvegarde excluent explicitement les données Android26–30, en complément des
exclusions cloud/transfert Android12+ et de allowBackup=false. Aucun droit ajouté.

Le code ContentResolver d'import et d'export technique est isolé dans DocumentAccess,
puis testé réellement avec un provider synthétique de l'APK de tests seulement.
Il refuse les URI file, borne les lectures et respecte l'annulation. Le provider ne
sert que sample.pdf/report.json ; il ne fait pas partie de l'APK applicatif et n'accède
à aucun document personnel. L'interface du sélecteur SAF n'est pas automatisée.

Aucun contrat versionné ni autre module métier n'est modifié. Le seul correctif dans
shared-runtime concerne DOCUMENTS/XML ; pas de nouveau moteur, catalogue LLM,
fonction OCR/XLSX/ZIM, matériel BRIDGE ou raccordement CORE/LLM.

## Résultats réellement exécutés

| Validation | Résultat | Preuve |
|---|---|---|
| Build/régression avant correctifs | 55 JVM, APK et Lint exécutés | baseline-build.log / baseline-validation.json |
| Régression finale, toutes tâches réexécutées | 59 succès, 0 échec/erreur/saut ; les 55 noms historiques sont présents | final-build.log / jvm-final.json |
| Démarrage Activity d'origine | Réussi sur Android26 | instrumentation-final.json |
| SQLite/FTS5 natif et migration d'origine | Réussi sur Android26 | instrumentation-final.json |
| Absence INTERNET d'origine | Réussie sur Android26 | instrumentation-final.json |
| Suite Android corrigée | 11 succès, 0 échec/saut, puis 11/11 à nouveau sur les APK finaux | instrumentation-after-fix.* / instrumentation-final.* |
| APK application et AndroidTest | Assemblés, hashes/tailles enregistrés | apk-manifest.json |
| Signature APK applicatif | Signature debug v2 vérifiée | apk-signature.log |
| Permissions APK applicatif | Aucune, notamment pas INTERNET | apk-permissions.log |
| Lint final | 0 erreur, 1 avertissement tiers | lint-final.json |
| Actions GitHub distantes | Non observées : tunnel HTTPS api.github.com refusé HTTP403 | ci-initial.json |

Les onze cas instrumentés finaux sont les trois historiques et huit scénarios :
PDF textuel/FTS5/DOCX non destructif, CSV exact et mémoire après réouverture,
GUARD/workflow échoué sûrement, restauration isolée, BRIDGE simulé/déconnexion,
import PDF/export technique par ContentResolver, bornes/annulation/URI interdites,
DTD DOCX refusées en UTF8 et UTF16.

La synthèse complète et les noms de chaque cas sont dans [validation.json](validation.json).
Les tests GUARD exécutent le code réel avec un hôte de confiance synthétique ;
BRIDGE reste simulé, CORE/LLM ne sont pas raccordés. Aucune simulation n'est comptée
comme intégration matérielle. Aucun zéro-test ou simple port ouvert ne vaut succès.

Les scénarios sont entièrement locaux : PDF de 621 octets généré pour le test,
CSV synthétique (0.1+0.2=0.3 après dédoublonnage), préférences explicitement confirmées
et répertoires de restauration isolés. Les originaux et sorties existantes sont
protégés contre l'écrasement. Le rapport technique exporté est vérifié sans le texte
ni le nom du document synthétique. Aucun fichier personnel importé.

## Lint et dépendances

Avant : 10 SetTextI18n, 1 DataExtractionRules, 1 TrustAllX509TrustManager.
Après : **seul TrustAllX509TrustManager subsiste** ; aucune suppression de règle,
baisse de sévérité ou changement abortOnError. DataExtractionRules demandait en
réalité fullBackupContent pour minSdk26, désormais fourni.

L'avertissement tiers vient de `bcpkix-jdk15to18:1.86`, classe
`org.bouncycastle.est.jcajce.JcaJceUtils$1.checkServerTrusted`, dont le bytecode
retourne sans vérifier le certificat. Analyse exacte : bouncycastle-lint-analysis.txt.
Aucun appel à ce helper n'est ajouté dans SERVICES ; aucun trust-all appliqué à
l'installation ou au runtime. L'APK n'a pas INTERNET. Cet avertissement reste visible
et devra être réévalué avant diffusion ou ajout d'un transport, sans être masqué.

Versions runtime et empreintes Gradle inchangées : Gson2.11.0, SQLite bundled2.5.0,
PDFBox Android2.0.27.0, Bouncy Castle1.86. Inventaire résolu : android-dependencies.log.
TLS, checksum du wrapper et verification-metadata.xml conservés. Aucune nouvelle
bibliothèque dans l'application ; adb-shell0.4.4 est uniquement un outil Cloud.
Le packaging contient SQLite JNI pour arm64-v8a, armeabi-v7a, x86 et x86_64 ; seule
l'exécution x86 sur API26 est qualifiée. Pas de scan CVE exhaustif revendiqué.

## Ressources et stabilité

Émulateur37.3.4 build16555280, AOSP API26 x86, TCG sans KVM, 1536 Mio, deux CPU,
480×800, SwiftShader, sans Play Store ni collecte de métriques. SDK vérifié :
sdk-provenance.json ; configuration : emulator-config.ini. Les scénarios ne touchent
pas aux protections RAM/batterie/température ; le code d'admission est conservé.

Huit checkpoints instrumentés sur les cas représentatifs du run final :
PSS maximal observé en fin de scénario **29059 KiB** ;
VmHWM RSS kernel maximal aux checkpoints **87824 KiB**.
Les durées de chaque scénario figurent dans validation.json (métriques monotones
Android). VmHWM est cumulatif depuis le démarrage du processus jusqu'au checkpoint,
pas un pic d'allocation isolé de chaque fonction ; les trois tests historiques
n'émettent pas ces checkpoints. PSS n'est pas une mesure continue.

La suite instrumentée corrigée est passée deux fois ; cela prouve la répétabilité
sur ce profil et ces petites fixtures, pas un stress test prolongé. Aucun gain
énergétique, mesure batterie, qualification thermique ou mesure sur téléphone réel.
Le test de mémoire JUnit ferme/réouvre SQLite et reconstruit Runtime, sans redémarrer
le téléphone. La sonde UI additionnelle est rapportée séparément ci-dessous.

## CI, commandes et artefacts

Workflow conservant trois jobs distincts : JVM, assemblage/Lint, instrumentation.
Les étapes APK applicatif, APK de tests, Lint et tests natifs sont séparées. Actions
fixées à des commits résolus par Git (action-pins.json), permissions contents:read,
annulation des runs périmés, artefacts avec SHA source et manifeste de hashes.
L'émulateur CI API26 x86 dispose d'un fallback logiciel sans donner de droit KVM.
La structure YAML et les noms d'inputs de l'action ont été relus/validés localement.

L'API Actions n'est pas accessible dans cette session (refus du proxy, pas une preuve
contre l'authentification GitHub). Aucune URL de run ni conclusion distante inventée.
Le workflow publié n'est donc **pas déclaré vert**. Consultation ultérieure :
https://github.com/bubth720/JARVIS-SERVICES/actions

Commandes exécutées, statuts préservés :

```sh
scripts/cloud-gradle.sh --offline -PwithAndroid :desktop:test :android:assembleDebug :android:assembleAndroidTest :android:lintDebug --rerun-tasks --no-daemon
PYTHONPATH=/tmp/jarvis-tools/adb-python python3 scripts/run-isolated-android.py --app android/build/outputs/apk/debug/android-debug.apk --tests android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk --expected 11 --output reports/002a/instrumentation-final.log
python3 scripts/write-apk-manifest.py --output reports/002a/apk-manifest.json
apksigner verify --verbose android/build/outputs/apk/debug/android-debug.apk
aapt dump permissions android/build/outputs/apk/debug/android-debug.apk
```

Les chemins d'activation, création de l'AVD et clients ADB sont expliqués dans
[002A_QUALIFICATION.md](../../docs/002A_QUALIFICATION.md). Les logs complets et les
échecs intermédiaires restent disponibles ; tooling-attempts.md donne leur chronologie.
Les APK debug restent des sorties locales ignorées par Git, pas des releases.

- `android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk` : 895604 octets ; SHA-256 `88888285dba3336c6c050cee590559578008e031ff9c4068944a3b4ce337e65c`.

- `android/build/outputs/apk/debug/android-debug.apk` : 14324635 octets ; SHA-256 `173f142a6fbf68cf3e4b3a52b27d473c2d799ca6e885842910ce88678070d04b`.

## Limites et prochaines actions

1. Qualifier les autres API/ABI et les appareils physiques uniquement après
   autorisation explicite d'installation. Aucun POCO, Redmi ou montre utilisé ici.
2. Observer les jobs distants après accès autorisé à api.github.com ; corriger tout
   écart propre au runner. La validation locale ne remplace pas cette observation.
3. Automatiser le sélecteur SAF réel et étendre les corpus DOCX/PDF/malformés et
   les mesures longues, sans introduire de documents personnels dans les preuves.
4. L'intégration réelle CORE/NEXUS/LLM est réservée à une mission distincte, prévue
   en 002B après validation des responsabilités/contrats. La synchronisation BRIDGE
   PC/téléphone/montre demeure future et doit préserver l'autonomie hors ligne.
5. Réexaminer le helper tiers trust-all avant tout transport ou diffusion de
   production, sans le rendre utilisable par l'application et sans baisser Lint.

## Sonde UI additionnelle et fichiers

La sonde UI additionnelle a finalement réussi : contenu synthétique exact confirmé
par le bouton de diagnostic, reçu de persistance observé, arrêt du processus par
am force-stop sur l'émulateur, PID 4210 → 4409, puis préférence retrouvée via le
bouton de lecture. Preuve : process-restart.json et scripts/probe-emulator-memory.py.
Elle n'est pas incluse dans les onze cas JUnit et ne vaut pas redémarrage du téléphone.
Deux tentatives incomplètes sont conservées : overlay ANR de SystemUI, puis injection
de texte synthétique tronquée ; dans ce dernier cas la confirmation exacte a été
correctement refusée par la sonde. L'exécution finale emploie une petite fixture
et attend le reçu avant l'arrêt du processus. Pas d'assertion affaiblie pour réussir.
Une tentative a rencontré le dialogue système « System UI isn't responding »
dans TCG ; ce symptôme ne doit être ni caché ni attribué à JARVIS sans preuve.

Les fichiers et justifications principaux :

- DOCUMENTS : Documents.java/DocxXml.java et DocxSecurityTest.java — compatibilité
  Android démontrée et durcissement portable, sans changer les contrats.
- Android : MainActivity.java/DocumentAccess.java, strings.xml, manifest et règles
  backup — I/O testable, bornes/annulation et avertissements applicatifs corrigés.
- AndroidTest : LocalQualificationTest.java, SyntheticDocumentProvider.java,
  manifest de test et sample.pdf — jeux de données isolés, sécurité et métriques.
- desktop/build.gradle — exclut le nouvel adaptateur Android du source-set JVM,
  qui continue de tester le code exact de l'adaptateur SQLite bundled.
- .github/workflows/verify.yml et scripts/ — CI séparée, pins immuables, artefacts
  sourcés et clients limités à l'émulateur, sans permission applicative ajoutée.
- docs/ et reports/002a/ — audit ADR, procédure, historique complet et preuves.

Risques résiduels : autres parseurs XML/versions Android non exécutés, corpus OOXML
complexes non exhaustifs, dépendance PDF/crypto tierce, lenteurs/ANR du système dans
TCG, CI distante inconnue et absence de qualification énergétique physique.
Les fonctions FOUNDATION ne sont pas supprimées ; les sept autres modules et les
schémas versionnés restent inchangés. Aucune fusion main.

Inventaire exhaustif des chemins modifiés : `changed_files` dans validation.json.
