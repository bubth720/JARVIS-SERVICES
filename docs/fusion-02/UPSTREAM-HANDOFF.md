# Handoff producteur SERVICES — revue des nouvelles livraisons voisines

Relecture finale en lecture seule ; aucun test voisin relancé ici, aucun code voisin
modifié. Les URLs/SHA/hashes des rapports sont dans evidence/upstream-final-reports.json.
Ces rapports sont des déclarations de leurs producteurs, pas des tests SERVICES.

| Propriétaire | Nouvelle tête observée | Conséquence pour cette livraison |
| --- | --- | --- |
| UNIFIED | 6de738633586743b3cf3f54e41ab4180c2cf0fee | 167 tests annoncés, CI indépendante réussie au code2fa138c ; drafts2 non approuvés, global BLOCKED |
| CORE | f6a3b2d0c280c69ef1570544fdbf5bcf5818810c | C0 passif/599 tests annoncés, enabled/canExecute=false, C1–C4 bloqués, aucune autorité mutatrice branchée |
| NEXUS | 06ccf98a7709364bf09cc1ecea1c35663d5787cf | 95JVM/71Robolectric et9 tests natifs par API24/25 annoncés ; SQLite3.9.2/FTS5 indisponible, refus sans reset/WAL préservé ; RAG reste UNSUPPORTED |

Le constat historique « aucune instrumentation NEXUS » ne s'applique plus à cette
tête. Le blocage actuel est la capacité FTS5 réellement absente des backends observés,
pas un manque de travail Kotlin. Le choix SQLite externe exige l'accord distinct
prévu par la mission ; aucun backend n'est ajouté dans SERVICES. Son PDF Android
pourra être consommé après revue de nos ports, pas en copiant le moteur ni en
injectant le runtime desktop.

## Compatibilité des propositions, sans adoption implicite

Les fichiers UNIFIED CONTRACTS.md et CORE INTEGRATOR-CONTRACT.md aux SHA ci-dessus
ont été relus intégralement. Ils demandent exactement la décision hôte sérialisée
avec la révocation, l'aperçu immuable, le consentement unique, la génération live,
la fermeture/relecture et UNKNOWN sans retry. Les tests SERVICES démontrent ces
ordres dans une autorité synthétique et un vrai provider, pas dans CORE.

| Point | Fixture SERVICES / proposition1 | Draft UNIFIED2 / prochaine revue |
| --- | --- | --- |
| Autorité | Host callback requis, fail-closed, aucune politique CORE codée | CORE garde session/politique ; adoption runtime non approuvée |
| Génération | Entier local >=0 ; fixtures commencent à0 | Wire draft exige1..2^53−1 : **incompatible directement**, host/version et bornes à négocier |
| États | PREPARED/COMMIT_STARTED/COMMITTED/UNKNOWN/REFUSED locaux | Wire PREPARED/DISPATCHED/COMMITTED/UNKNOWN plus décision/effet : pas de cast/renommage implicite |
| Liaisons | Preview en mémoire lie session/request/source/contenu/opération/destination/deadline | Digest d'opération/version, séquence et preuve d'autorité devront être persistés selon contrat approuvé |
| Reçu durable labo | ID/état/digest/contexte destination synthétique ; token host non sérialisable | Pas un OperationReceipt2 complet ; migration/format/séquence/avis révocation à revoir |
| Après engagement | Peut terminer, pas de rollback promis ; test de barrière | Avis PERMISSION_REVOKED et politique de restitution CORE à intégrer après revue |
| Ancien CORE receipt/1 | Non lu ni modifié | COMPLETED/REFUSED ne devient jamais COMMITTED par renommage |
| Recherche | Citation de pages sélectionnées ; URI/hash à relier par l'hôte | Résolution NEXUS/CORE et grants/sources/revisions restent à composer, pas un RAG SERVICES |

Ces écarts sont signalés **avant** toute implémentation consommatrice ou activation
sensible. Aucun schéma UNIFIEDv1, draft2, reçu CORE ou génération de mémoire personnelle
n'est réécrit pour rendre la proposition compatible artificiellement. L'interface
`CommitAuthorization` est compilable mais son adoption reste REVIEW_REQUIRED.

## Prochain lot réellement admissible

1. CORE/UNIFIED/SERVICES examinent ces écarts et fixent une version, l'ordre durable
   décision/consommation/reçu/effet, le nonce, les erreurs et la restitution après
   révocation. Garder la session et l'arbitre existants, aucun second coordinateur.
2. CORE recompile les sources des ports aux SHA approuvés, conserve OFF par défaut,
   puis qualifie l'hôte réel, rotation et force-stop/ACK entre étapes sur copies.
3. Un pilote CSV/TXT peut être proposé indépendamment du RAG non disponible ; il
   nécessite toujours cette revue d'autorité, pas un opt-in contournant C0.
4. NEXUS poursuit sa décision de backend séparément. LLM/VOICE/INTERFACE/EVOLUTION
   ne deviennent pas des prérequis artificiels du pilote documentaire.

Aucune action sensible, message à un owner, modification interrepo ou merge n'est
réalisé par ce handoff. Il rend la branche et les preuves disponibles à la revue.
