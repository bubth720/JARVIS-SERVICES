# SERV-03 — workflows résumables et boucle locale de laboratoire

Dépôt : bubth720/JARVIS-SERVICES. Branche : `feat/serv-03-services`.
Base : SERV-02 `f7681707e7308d4b967e642d2f807f3e354209cc`, après SERV-01
`d300642a4f10b53bdc988a43e0d7abb05c0d2253`. Fiche centrale vérifiée :
`67e7f798b8bbcb8391e0d57ea5b886a2a9edf4db`.
Dépendance UNI-01 réellement livrée et relue :
`d947dadf73f8dec91543a3f86a1aa2ffef0b0d2f`.

## Code livré

- WorkflowDefinition : DAG immuable de dix étapes maximum, contrôle des IDs,
  dépendances/cycles, versions, délais et digest de définition. Les arguments
  restent en mémoire hôte ; le journal persiste leurs empreintes, jamais le texte.
- WorkflowEngine : admission à la demande, une étape prête par appel, réutilisation
  des vrais outils du registre, permissions hôte et approbation exacte pour chaque
  action. RequestId déterministe par workflow/étape et étapes réussies jamais rejouées.
- WorkflowStateStore : transitions et journal append-only dans la même transaction
  SQLite. Résumption après fermeture/réouverture entre étapes. RUNNING récupéré,
  timeout ou interruption ambigus deviennent UNKNOWN, jamais reset/retry implicite.
  Aucun service Android permanent ni nouvelle autorité sur les ressources natives.
- CompensationJournal : crée ses propres temporaires `.workflow-UUID.tmp`, puis
  n'autorise leur suppression que si nom réservé et digest sont encore identiques.
  Les exports finaux et fichiers originaux ne peuvent pas être compensés.
- BridgeOutbox/Inbox : séquences, vecteurs monotones, digests, reçus, déduplication
  et bornes de 1024 messages par file ; reçu durable sans exécution d'outil distant.
  Perte d'ACK => UNKNOWN ; un nouvel envoi est une action explicite, pas un retry
  automatique. Un ACK persisté évite tout nouvel envoi.
- TransportPort.Disabled reste le comportement de production. Aucun lien n'est
  ajouté au Bridge.Simulator historique ou à Runtime Android.
- LocalLoopbackTransport/LoopbackReceiver : vrais processus JVM de laboratoire,
  stdin/stdout bornés, classe enfant fixe, racine temporaire synthétique imposée.
  Aucun socket, appairage, service exporté ou permission INTERNET ajoutés. Ce
  mécanisme ne constitue ni authentification de production ni sandbox OS.

Le codec UNI-01 exact RequestEnvelope/ContractSupport est repris **uniquement dans
le module desktop**, avec provenance et SHA-256 dans UNI-01-SERVICES-LOCK.json.
Les schémas UNIFIED ne sont pas modifiés, le codec n'est pas embarqué dans Android.
`python3 scripts/check-services-contract-lock.py` vérifie hors ligne les deux
empreintes de sources vendoriées ; commande exécutée avec succès (2 fichiers).
Le validateur refuse les requêtes mal formées/incompatibles ; l'enveloppe déclarée
CORE/services et authorization_ref ne constituent pas une identité authentifiée.
Les messages du labo ne contiennent que des références opaques et des métadonnées,
aucun prompt/document/audio, et ne déclenchent aucune opération distante.

## Démonstration réellement exécutée

`./scripts/cloud-gradle.sh -PwithAndroid :desktop:test :desktop:workflowLabDemo :android:assembleDebug :android:assembleAndroidTest :android:lintDebug --no-daemon --max-workers=2`

Résultat : BUILD SUCCESSFUL, 73 tâches dont13 exécutées, 23 octobre2026.
Extraits fonctionnels observés :

```
phase1: search succeeded; clean JVM stop with remaining steps pending
phase2: CSV and approved DOCX succeeded after reopening the same SQLite database
workflow: three real tools; search executed once across two JVMs; DOCX verified
bridge outbox: ACKED
bridge: real receiver child JVMs; one durable inbox entry after repeated delivery; no tool executed remotely
```

La CLI fabrique ses propres sources synthétiques. Deux processus JVM distincts
exécutent successivement recherche, CSV et export DOCX avec accord synthétique
explicite. Il s'agit d'une sortie propre entre étapes, **pas d'une coupure électrique
au milieu d'une écriture**. L'inconnu en cours est couvert séparément par injection
logicielle d'état RUNNING, conservé comme UNKNOWN sans réexécution.

## Tests et vérifications

**92 tests JVM, 0 échec, 0 erreur, 0 ignoré** : 59 historiques,10 SERV-01,
15 SERV-02 et8 SERV-03. Les totaux sont cumulatifs, ne pas les additionner comme
s'il s'agissait de suites distinctes. Cas : vrai DOCX relu, réouverture SQLite,
étape achevée non rejouée, définition modifiée/cycles refusés, consentements,
UNKNOWN, compensation réservée, deux processus réels, ACK/déduplication durables,
séquence sautée, vecteur régressif, digest falsifié, JSON flottant/doublonné,
contrat invalide et transport désactivé.

Build diagnostic APK et APK d'instrumentation réussis. Lint : **0 erreur,1 warning**
tiers bcpkix1.86 déjà documenté en SERV-02 ; aucune suppression ni baisse de seuil.
Toolchain : JDK17 complet existant, Gradle8.13 épinglé, Java11 sources et Android
minSdk26 inchangé ; caches/user-home/SDK hors dépôt, proxy/TLS conservés.
Aucune instrumentation Android exécutée ici, aucune installation personnelle,
aucun GGUF/JNI/VOICE, aucune donnée réelle ni migration.

## Limites et intégration future

Le scheduler est un composant trusted_in_process : le futur CORE reste propriétaire
des décisions et de l'émission des grants. Aucun nouvel automate ne s'autorise
lui-même. Une action incertaine n'est ni réessayée ni annulée par effacement de son
journal. La compensation de fichiers définitifs est volontairement interdite.
Les files sont une fondation locale de transport, pas une synchronisation Android/PC
qualifiée. Signature, chiffrement/appairage et transport de production nécessitent
une mission et validation distinctes. L'export SAF SERV-02 reste compilé et testé
au niveau protocole JVM, non qualifié sur un provider Android réel.

Les nouveaux namespaces Store ne sont pas ajoutés au snapshot historique :
export/restauration/migration de ces journaux n'est pas revendiqué. Les bornes
pleines provoquent un refus explicite, sans purge cachée des preuves.
Aucun autre dépôt modifié, aucune fusion dans main, aucune action destructive.
