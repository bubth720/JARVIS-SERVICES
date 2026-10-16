# Audit en lecture seule — 16 octobre 2026

Le dépôt SERVICES a été repris au commit `23b8fd9689175118baea2727ff6b45221014aede`,
branche `mission-001-services-foundation`. Deux fichiers suivis seulement : README
et cahier des charges, lu intégralement. Aucun contrat ni code existant SERVICES.
Aucun autre dépôt n'a été cloné ou modifié : les archives distantes ont été lues
en mémoire et les branches interrogées par `git ls-remote`.

| Projet et ref consultée | SHA observé | Observation directe |
|---|---|---|
| JARVIS-UNIFIED main | e4435f4bd60c5fdc5595c411cc8cab9da7ef020b | README seul |
| JARVIS-UNIFIED docs/adr-001-approved-2026-10-15 | e0e6f69c4908a152da4e43864a08fef0c8f7a7af | ADR approuvée avec précisions, schémas fermés v1 |
| JARVIS-LLM main | 917e3351f9b525857b6aa019e98d07872d8f6e76 | Étude Phase 0 sur main |
| JARVIS-LLM mission-003b-resources | 1756e29557287550b5cfdffdd911cada2a302db6 | Interface LanguageModel et GenerationRequest réels |
| JAVIS-Local-v2 main (CORE) | 226e2e8cc3eebd479805b89d035eb5c1cbcad540 | LlamaLocalEngine interne, pas un port SERVICES IPC |

Le nom initial testé `bubth720/JARVIS-CORE` répond « Repository not found » ; cela
n'établit pas l'absence du projet. L'identité CORE réelle a été retrouvée dans les
références UNIFIED puis son code a été lu sur `JAVIS-Local-v2`.

## Contrats réels et écarts

UNIFIED utilise `protocol_version=1.0`, `contract_version=1.0.0`, correlation_id,
scope/session/authorization_ref, limites, délai monotone expéditeur, cancellation_id
et payload de références privées. Les capacités sont une énumération fermée ; aucun
ajout SERVICES implicite n'est possible. Sa réponse inclut provenance, vérification,
résultat référencé et arrêt natif. Les schémas restent proposés selon l'ADR.

L'ADR approuve seulement l'orientation modulaire : CORE autorise, LLM infère,
UNIFIED coordonne les preuves, SERVICES ne doit pas devenir un orchestrateur
central. Transport, identité IPC, arbitre global et migrations restent ouverts.
SERVICES conserve ses contrats en processus dans son propre namespace, expose
un mock CORE et ne modifie ni contrats UNIFIED ni données historiques.

LLM `domain/src/main/kotlin/fr/jarvis/domain/Conversation.kt` définit LanguageModel :
prepareOperation, load(path,config), generate(GenerationRequest), requestStop,
unload et close. GenerationRequest borne huit messages, contexte 512..2048,
sortie 1..256 et threads 1..4, avec restrictions spécifiques SMALL/tablette. Il
n'expose pas directement notre résultat structuré/citations. Un futur adaptateur
réel devra respecter ces budgets, le tokenizer, l'arbitrage et prouver la fin native.

CORE `app/src/main/java/com/stark/jarvislocal/LlamaLocalEngine.kt` possède warmUp,
generate(prompt,systemPrompt,settings), isLoaded et gestion interne du modèle.
Il n'expose pas un service de génération inter-applications SERVICES vérifié.
Aucun moteur n'est recopié et aucune signature externe n'est modifiée.

## Raccordement futur

1. Faire négocier le mapping de versions et les capacités par CORE.
2. Faire transmettre à SERVICES des références locales résolues par l'hôte,
   avec grants minimaux et confirmation exacte. Ne pas accepter de grants du LLM.
3. Implémenter un adaptateur LLM respectant budgets, refus matériel, arrêt et
   déchargement attestés ; exécuter de vrais tests avec le backend existant.
4. Qualifier identité/transport et appareils séparément. Aucun succès simulé
   dans cette mission ne vaut intégration native ou qualification physique.
