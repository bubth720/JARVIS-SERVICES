# SERV-04A — Bibliothèque Android API24 utilisable en processus

Date : 24 octobre 2026. Dépôt `bubth720/JARVIS-SERVICES`, issue [#1](https://github.com/bubth720/JARVIS-SERVICES/issues/1).
Branche : `feat/serv-04a-android-tool-port`.
Base vérifiée : `3decdc47d82f9b43713c48cab244762941770a81` (SERV-03), descendante de SERV-02 `f7681707e7308d4b967e642d2f807f3e354209cc` et SERV-01 `d300642a4f10b53bdc988a43e0d7abb05c0d2253` ; les trois références distantes ont été revérifiées.
Le commit de livraison est celui contenant ce rapport ; son SHA exact est communiqué dans le compte rendu et vérifié par `git ls-remote` après publication. Aucun merge.

## Résultat livré

- `services-portable-api` contient les classes existantes déplacées, non des copies concurrentes : Store/Database, contrats internes, Task, Data, extraction/citations, registre, dispatcher et ApprovalGate. `shared-runtime` reste la façade JVM historique et dépend de cette bibliothèque.
- La partie permissions/confirmation/audit de Guard est partagée via `AccessGuard`. Le Guard historique conserve son confinement `Path` dans la façade JVM. Les codecs TXT/DOCX et transformations CSV sont partagés. Les 92 tests historiques sont conservés.
- `services-android-adapter`, bibliothèque **minSdk24 distincte**, utilise `ContentResolver` et la SQLite système pour un journal neuf privé au SDK. Aucun JDBC, aucune nouvelle bibliothèque native, aucun scan global, aucun réseau ni permission Android ajoutée. Le diagnostic Android historique reste minSdk26 : aucun fichier sous `android/` n'est modifié.
- `ServicesAndroidToolPort` sélectionne volontairement une URI `content:` en lecture seule, extrait un TXT ou un PDF synthétique réel de deux pages, puis recherche littéralement les pages sélectionnées avec citations SHA-256/page/offsets UTF-16. Les pages restent en mémoire de session ; ce n'est ni un index NEXUS ni une recherche globale.
- `data.csv.normalize` réutilise Data pour normaliser/dédoublonner et produire le CSV réel, y compris son format de sortie entièrement entre guillemets.
- Principal et permissions sont des entrées de l'hôte de confiance, jamais des arguments d'un outil ou d'un document. Le SDK n'atteste pas à lui seul une autorisation délivrée par CORE.
- Refus, identifiants de requêtes et états sont journalisés sans texte, CSV, URI, nom de source ou principal. Un identifiant déjà reçu n'est pas réexécuté. Un reçu inachevé devient `UNKNOWN` après redémarrage ; aucun reset ni retry automatique.
- Un seul propriétaire du journal est admis par verrou de fichier OS, gardé jusqu'à la fermeture effective. Les bases sans marqueur SDK ou incompatibles sont refusées sans adoption ni réinitialisation. L'ouverture SQLite peut toucher ses métadonnées/fichiers auxiliaires ; aucune immutabilité absolue du système de fichiers n'est revendiquée.
- La persistance du reçu précède la livraison du résultat. Une erreur de persistance donne `UNKNOWN / RECEIPT_UNCONFIRMED`, sans valeur de succès. La fermeture attend la fin observée des workers et de la sélection de flux, sans bloquer l'appelant ; `closeAsync()` permet d'attendre la fermeture réelle de la DB hors du thread UI. Une construction refusée libère aussi la DB et son verrou.

## Capacité volontairement indisponible

**L'export DOCX du nouveau port Android est désactivé : `SAF_EXPORT_UNQUALIFIED`.** Aucun document cible n'est ouvert en écriture ou tronqué. Une qualification séparée doit établir création fraîche, aperçu/consentement exact, ACK, relecture et gestion `UNKNOWN` ; un fournisseur SAF n'offre pas une preuve générique de rollback. Le mécanisme historique SERV-02, ses tests et ApprovalGate restent présents. L'import DOCX passe par le codec commun mais n'a pas été qualifié sur appareil dans cette mission.

Cette limitation est explicitement admise par les critères minimaux de réception de l'issue. La bibliothèque de recherche/CSV/permissions est livrée ; la totalité de l'export Android prévu reste partielle.

## API pour le futur consommateur CORE-03A

```java
// Uniquement dans un parcours laboratoire choisi par l'utilisateur.
// Principal/grants viennent de la politique CORE, jamais du modèle ou du document.
ServicesAndroidToolPort port = new ServicesAndroidToolPort(
    context, hostPrincipal, hostGrantedPermissions);
String revision = port.selectSource(explicitlySelectedContentUri, new Task(10000));
ToolInvocation query = new ToolInvocation(UUID.randomUUID().toString(),
    "documents.search", "1.0.0", Collections.singletonMap("query", "alpha"), 10000, null);
ToolDispatcher.Handle handle = port.dispatch(query);
// Consommer le résultat hors UI, corréler la session côté CORE et refuser les réponses tardives.
// handle.cancel() demande une annulation coopérative, pas une interruption native.
port.closeAsync(); // Attendre ce CompletionStage hors UI avant de réouvrir le journal.
```

Version locale : `jarvis.services.inprocess/1`. Les schémas UNIFIED v1 ne changent pas et aucune compatibilité IPC n'est annoncée. Un hôte recompilé doit dépendre de `:services-android-adapter` et de son `api :services-portable-api`, pas de `:shared-runtime` ni de `:desktop`.

La séparation Guard/AccessGuard change des signatures binaires JVM : les consommateurs doivent être recompilés, pas remplacer aveuglément un ancien JAR. Les usages historiques sont vérifiés par les tests et builds ; aucune compatibilité ABI binaire complète n'est prétendue.

**CORE-03A n'est ni commencé ni raccordé.** Son autorité de session, sa corrélation et son admission globale restent à intégrer lors d'une mission ultérieure. `Task.HEAVY` est seulement l'exclusion locale SERVICES, pas un arbitre CORE–LLM–VOICE.

## Limites bornées

Sources : 2 Mio maximum par flux, huit sources, 100 pages et 250 000 caractères extraits au total par session. Recherche : 500 caractères de requête, 50 citations, 600 caractères par citation. Requêtes : 4096 reçus maximum, puis refus explicite ; aucune suppression automatique du journal. Dispatcher : un worker, huit places en attente. Ces limites d'entrée ne prouvent pas un plafond de RAM du PDFBox ; le coût mémoire doit être qualifié sur l'appareil cible.

Les lectures d'un fournisseur et appels PDFBox sont coopératifs : une lecture bloquée peut retarder la clôture ; ni l'annulation d'une tâche ni une fermeture demandée ne prouvent l'arrêt d'un traitement natif. Les conversations/identités/profils vocaux et budgets matériels CORE ne sont pas modifiés. Aucun LLM ajouté, aucune activation d'Athéna, de VOICE ou de BRIDGE.

## Vérifications réellement exécutées

Environnement : Linux x86_64, JDK17.0.16, Gradle8.13, SDK Google35/build-tools35.0.0 ; plateforme Google Android24 révision3 ajoutée pour le contrôle des API. Proxy et TLS conservés. Les nouvelles dépendances de test sont verrouillées SHA-256 ; les 524 empreintes préexistantes restent présentes, 78 nouvelles empreintes ajoutées, aucun assouplissement de vérification.

```sh
./scripts/cloud-gradle.sh -PwithAndroid :desktop:test :desktop:workflowLabDemo \
  :services-android-adapter:testDebugUnitTest :services-android-adapter:assembleDebug \
  :services-android-adapter:lintDebug :services-android-adapter:assembleAndroidTest \
  :android:assembleDebug :android:assembleAndroidTest :android:lintDebug \
  --no-daemon --max-workers=2
./scripts/cloud-gradle.sh :desktop:test --no-daemon --max-workers=2
python3 scripts/check-services-contract-lock.py
python3 scripts/check-api24-compile.py --sdk "$ANDROID_HOME" \
  --dependency-cache "$GRADLE_USER_HOME/caches/modules-2/files-2.1" \
  --javac "$JAVA_HOME/bin/javac" --output build/serv04a-api24
git diff --check
```

Dans cet environnement cloud, les variables JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME/ANDROID_USER_HOME et `JAVA_TOOL_OPTIONS=-Duser.home=/workspace/.jarvis-core-tools/user-home` désignent des répertoires écrits sous `/workspace` ; aucune variable HOME système n'a été détournée.

Résultats :

- **94 tests JVM réussis, 0 échec, 0 ignoré** : les 92 historiques + deux nouveaux tests de reçu incertain et clôture après retour observé d'une opération annulée.
- **40 tests Robolectric réussis, 0 échec, 0 ignoré**, dix scénarios sur chacun des SDK simulés24/25/26/35 : CSV, refus des permissions, recherche/citation TXT, PDF réel deux pages, annulation/URI/limites/DOCX refusé, propriétaire concurrent, reprise `UNKNOWN`, clôture pendant flux bloqué, corruption et préservation d'une DB non possédée.
- Démo historique : workflow trois vrais outils, DOCX relu, reprise entre deux JVM et loopback isolé dédoublonné. Cette démo JVM ne valide pas l'export du nouveau port Android.
- AAR min24, APK d'instrumentation du SDK et APK historiques compilés ; Lint SDK et historique **0 erreur / 1 avertissement** chacun, provenant du TrustManager BC déjà présent. Aucun avertissement supprimé ni sévérité diminuée.
- La compilation directe des **22 sources** portables et Android contre le vrai `android.jar` API24 réussit. Le compilateur utilise seulement les stubs lambda officiels de build-tools comme complément de bootclasspath, non empaquetés. Aucun bootclasspath Java17 susceptible de masquer des API Java absentes en Android24. Le script contrôle les SHA Gson/PDFBox contre le verrou Gradle.
- Lint Android traite la bibliothèque Java portable comme externe ; `checkDependencies` n'est pas une preuve d'analyse exhaustive de ses sources. Le contrôle direct de compilation API24 complète cette limite ; aucune qualification physique des dépendances n'est inventée.
- Verrou contrats SERVICES : deux fichiers UNI-01 exacts, vérifiés. Sources/build/manifest du diagnostic historique inchangés. Aucun `java.sql`, `java.nio.file` ou `java.time` dans les sources du nouveau chemin Android ; aucun JNI dans le JAR/AAR produits.

Échecs intermédiaires corrigés : classpath du receiver JVM après extraction de Store (ajout de Guard à son classpath), marqueur `com.android.library` résolu vers le module AGP officiel de même version, empreintes des nouvelles dépendances ajoutées, accès des caches cloud déplacé de `/home/agent` en lecture seule vers `/workspace`, gestion de la table système `android_metadata` au premier démarrage. Aucun test n'a été désactivé.

**Non exécutés** : instrumentation sur émulateur24/25/26/35 et appareil physique, fournisseur SAF réel et export Android, mesure RAM/thermique/batterie. Les tests Robolectric utilisent les implémentations hôtes Android et un fournisseur synthétique : ils ne sont pas des essais sur POCO/Redmi ni une preuve de qualification Android physique. Le workflow GitHub est préparé ; sa réussite distante doit être constatée séparément.

## Fichiers et prochaine action

Nouveaux modules `services-portable-api/`, `services-android-adapter/` ; adaptations de `settings.gradle`, `shared-runtime/build.gradle`, façades Documents/Guard, classpath `desktop/.../LocalLoopbackTransport.java`. Nouveaux tests `ToolReceiptLifecycleTest.java`, `ServicesAndroidToolPortTest.java`, `AndroidToolPortInstrumentedTest.java`. Script `scripts/check-api24-compile.py`, verrou Gradle enrichi, workflow CI, README, ce rapport et reçu JSON.

Prochaine action proposée : examiner ensemble la réception de NEX-06A et de ce port, puis autoriser séparément CORE-03A si ses dépendances sont réellement satisfaites. La qualification SAF DOCX complète reste distincte. Aucun changement sensible, installation, migration, merge ou intervention dans un autre dépôt n'a été réalisé.
