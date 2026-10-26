# Proposition de contrat consommateur — FUSION-02 S1

Version additive `jarvis.services.commit-proposal/1`, package `api.proposal`.
**Proposition compilable, sans raccord au runtime `ServicesToolPort/1`.**
L'implémentation de politique reste dans CORE. Aucune modification des schémas
UNIFIED v1, de Guard, ApprovalGate ou du catalogue existant.

## Engagement et responsabilités

1. CORE prépare un `Preview` immuable : UUID session/requête, génération, opération
   fermée export.csv/docx, SHA256 source et contenu exact, destination précise
   (arbre SAF + nom neuf en laboratoire), deadline monotone.
2. CORE montre cet aperçu et délivre un `ConsentToken` opaque après confirmation
   explicite. Un texte documentaire, un modèle ou des arguments ne créent aucun droit.
3. Un seul worker appelle `authorizeAtCommit(preview, token, effect)`.
   CORE sérialise la révocation, l'annulation sticky et cette décision sur son
   même verrou. Vérification atomique de l'identité, de tous les champs, de la
   génération courante, de la deadline, du quota d'opérations et de l'usage unique.
   Consommer la confirmation et enregistrer la requête engagée est le
   **point de linéarisation**, avant le callback d'effet et sans IO sous ce verrou.
4. Une révocation antérieure refuse le callback : zéro création/écriture. Une
   révocation postérieure peut précéder le premier octet et laisser aboutir
   l'opération déjà engagée. CORE doit afficher cet avis, pas promettre l'effacement.
5. SERVICES persiste COMMIT_STARTED avant la première mutation du provider.
   La destination créée est journalisée aussitôt connue. Le callback doit être
   invoqué au plus une fois ; une autorité absente doit refuser avant tout effet.
6. Close réussi + relecture de tous les octets et SHA256 attendu + original
   inchangé autorisent COMMITTED. Toute exception après engagement, ACK perdu,
   état inachevé au démarrage => UNKNOWN, destination potentiellement partielle,
   jamais de répétition automatique. Une erreur du journal ne prouve aucun succès.

Le retour de `authorizeAtCommit` n'est pas un contrat transactionnel distribué.
Une exception après exécution peut signifier une perte d'ACK. Ni timeout ni
`Future.cancel` ne prouvent l'arrêt d'un appel natif/ContentResolver bloqué.
Les tests de barrières isolent les deux ordres de décision. Les tests de reprise
SQLite injectent un reçu abandonné : ce sont des fenêtres de crash simulées,
pas une preuve de coupure électrique réelle.

## Laboratoire livré

`LabSafExporter`, `FusionPickerActivity` et l'autorité synthétique sont
exclusivement sous `src/androidTest`. `FusionDocumentsProvider` est dans un
APK séparé `services-saf-fixture`, min24, `testOnly=true`, distinct par UID.
Aucun de ces composants ne figure dans l'AAR ou le diagnostic historique.
Le provider conserve MANAGE_DOCUMENTS. Un receiver de contrôle des fautes,
protégé par une permission **signature de laboratoire**, permet uniquement
normal/corrupt/deny ; cette permission est demandée par l'APK AndroidTest seul.
Aucune permission de production n'est ajoutée, aucune signature existante changée.

Le sélecteur système choisit réellement les sources et l'arbre de destination,
avec concessions persistantes entre UID distincts. Seule l'autorité du provider
de fixtures est admise par l'exporteur ; celui-ci crée un nom UUID nouveau après
confirmation. Pas de suppression de sortie ni de rollback annoncé sur un provider
externe. Quota journal32, entrée2Mio, verrou de fichier inter-instances et
sérialisation du worker. Le journal est une base laboratoire neuve, sans migration
ni accès aux DB historiques. La révocation OS des concessions persistables est
un scénario distinct de la fixture qui refuse ses propres lectures.

Les campagnes et leurs résultats réels sont détaillés dans REPORT.md et
VALIDATION.json ; l'existence d'un test ne prouve pas sa réussite. La qualification
d'un provider utilisateur arbitraire, des URI aliases, du close bloqué,
des lifecycle/rotation et d'une vraie interruption en cours de mutation reste un
prochain gate avant tout raccord sensible. Le scénario de reçu abandonné est une
injection de fenêtre de crash, pas une coupure physique de processus pendant IO.

## Décision demandée aux propriétaires (sans activation implicite)

CORE et UNIFIED doivent arbitrer le point d'engagement, le format de preuve de
révocation après engagement, la génération entre processus, la persistance du
consentement, les budgets/deadlines et les erreurs de protocole. Ensuite seulement,
une API runtime versionnée peut être conçue et intégrée sur leur branche.
Le port actuel garde `SAF_EXPORT_UNQUALIFIED` même si la fixture réussit.
L'intégration CORE, un transport IPC/BRIDGE, du natif SQLite supplémentaire et
l'utilisation de données/appareils personnels restent hors de cette livraison.
