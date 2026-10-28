# SERV-PF-02 — banc de reçus isolé (lot 3)

Reprise vérifiée le 28 octobre 2026 UTC. Branche `codex/prefusion-lot3-services`,
base mission `7c9b0efe10187edadbf8555fefe4c98702fa5e98` ; référence précédente
`ea541d20b08f7ad0949354829a977f6752cbffa3` vérifiée ancêtre. Le SHA de livraison
est le HEAD de cette branche contenant ce rapport (pas de SHA auto-référentiel).
Cible de PR : `mission/prefusion-lot3-20261027`, jamais `main`.

## Livré et limites

Deux classes de test seulement : `ReceiptContractBench` (horloge injectable,
journal mémoire, aperçu exact, consentement lié et consommé une fois) et 14 cas
`ReceiptContractBenchTest`. Annulation/révocation/expiration avant engagement :
aucun effet. Commande dupliquée, token étranger, journal corrompu/réordonné ou
d'une autre génération sont refusés. Perte d'ACK, close/readback incorrects,
erreur d'effet ou journal après décision : UNKNOWN conservé, aucun retry.
La révocation après décision ne promet pas d'annuler l'effet déjà engagé.

Le banc ne fournit ni autorité CORE réelle, ni persistance durable, ni mutation
SAF, ni attestation UID, ni libération JNI. Les résultats sont des tests de contrat
JVM, pas une preuve Android. Production, bases, contrats, permissions et signatures
restent inchangés ; `SAF_EXPORT_UNQUALIFIED` est conservé.

## Vérifications réellement obtenues

Les XML et logs du travail interrompu ont été relus avant toute reprise ; les
classes de test n'ont pas changé depuis leur exécution. `EVIDENCE.json` conserve
les totaux et empreintes XML sans enregistrer leurs propriétés d'environnement.

| Vérification | Résultat observé |
|---|---|
| `:desktop:test` | 121 réussis (107 historiques + 14 nouveaux), 0 échec/erreur/saut |
| `:services-android-adapter:testDebugUnitTest` | 40 Robolectric réussis, 0 échec/erreur/saut |
| AAR debug + APK androidTest adaptateur + Lint debug | BUILD SUCCESSFUL ; 79 tâches, 14 min 06 s |
| Diagnostic historique APK debug + APK androidTest + Lint debug | BUILD SUCCESSFUL ; 71 tâches, 4 min 38 s |
| Compilation des 24 sources contre boot classes API24 | exit 0 ; compilation seule |
| `python3 scripts/check-services-contract-lock.py` | 2 références vérifiées |
| `python3 scripts/check-fusion02-evidence.py` | preuves historiques cohérentes ; PARTIAL conservé |
| Instrumentation API24/25/26/35 au nouveau SHA | NOT_RUN localement ; 15 cas exigés par API dans CI |
| Appareil personnel / export productif / raccordement CORE | NOT_RUN / désactivé / non réalisé |

Commandes portables exactes : voir `EVIDENCE.json`. La reprise utilise JDK17,
Gradle8.13, SDK24/35 et les dépendances vérifiées du dépôt. CI conserve JDK21 ;
aucune nouvelle dépendance. Avertissements : Gradle9 non compatible avec des
fonctions dépréciées, SecurityManager historique déprécié, Lint n'analyse pas
l'API Java portable comme module Android. Aucun avertissement supprimé.

Le run historique 40412745341 décrit dans le rapport lot1 appartient au SHA
365da117… et n'est pas attribué à ce nouveau lot. La liste Actions de la branche
mission ne suffit pas à établir un résultat pour le futur SHA : campagne à observer.
Aucun test non exécuté n'est compté comme réussi.

## Décisions et suite

`ADR-002-PROPOSEE.md` est NON APPROUVÉE : autorité CORE et fence live, ordre des
journaux/consentement/effet, preuve de fermeture + ACK + readback et réconciliation
UNKNOWN exigent validation distincte avant branchement. Génération0 n'est jamais
convertie en1 ; DISPATCHED/COMMIT_STARTED et COMPLETED/COMMITTED restent distincts.

CI existante conservée, seul ajout du déclenchement sur la branche lot3. Les
quatre campagnes de 15 cas ne sont ni réduites ni remplacées par le banc.
Prochaine action : contrôler la CI exacte au SHA publié, puis soumettre les trois
décisions ADR à revue inter-modules. Aucune activation de production par un vert CI.
Rollback : revert du banc/docs et du déclenchement de branche ; aucune donnée à
migrer, aucun journal utilisateur à purger, aucun consentement à restaurer.

## Publication

La création de PR via l'API GitHub est bloquée dans cet environnement : tentative
coordonnée sur INTERFACE, `POST https://api.github.com/graphql: Forbidden`.
Cette erreur n'est pas interprétée comme un défaut du code ; aucun contournement
ni répétition du POST. Le corps prêt à publier est `PR-BODY.md`. Lien de préparation :
https://github.com/bubth720/JARVIS-SERVICES/compare/mission/prefusion-lot3-20261027...codex/prefusion-lot3-services?expand=1
Choisir **Create draft pull request**, base mission/prefusion-lot3-20261027.
La branche Git doit être vérifiée par `git ls-remote` après publication ; son SHA
sera fourni dans le bilan central, indépendamment de l'impossibilité de créer la PR.
