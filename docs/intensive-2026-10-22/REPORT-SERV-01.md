# SERV-01 — registre et dispatch local

Base technique : `0f462c8ad5529ba974cefa17fe6562c8d8461d26` ; base de branche
`67e7f798b8bbcb8391e0d57ea5b886a2a9edf4db` (diff vérifié : uniquement quatre fiches).
Branche : `feat/serv-01-services`. Autorisation : ordre global de développement
intensif, puis demande d'implémenter le plan, 22 octobre 2026.

## Code livré

- Registre découvrable de descripteurs versionnés persistés dans un namespace
  additionnel de Store ; aucun changement de schéma historique. Une entrée disque
  seule ne rend pas un outil exécutable : l'hôte doit enregistrer son adaptateur.
- Invocation à arguments chaîne strictement nommés et immuables, résultats
  SUCCESS/REFUSED/FAILED/UNKNOWN corrélés, exécuteur local borné (un worker, huit
  attentes), annulation coopérative et deadline incluant le temps en file.
- Trois adaptateurs réels : Documents.search, Data.csv/normalize/deduplicate,
  Documents.export DOCX. Les huit modules restent intacts et compilables ; leurs
  autres opérations ne sont pas annoncées comme disponibles via ce catalogue.
- Approbation uniquement par API hôte de confiance : contenu exact à afficher,
  principal, identifiant de requête et digest du descripteur liés au reçu. Le reçu
  est consommé transactionnellement avant tout effet. Journaux sans contenu ni
  chemins. Une interruption laisse un résultat UNKNOWN, sans réessai automatique.
- Les reçus persistés contiennent un epoch hôte et une expiration monotone : une
  approbation en attente est **inutilisable après redémarrage**, conservée pour
  diagnostic et à renouveler après une nouvelle décision utilisateur. Le journal
  est borné à 1024 reçus (refus explicite au-delà), sans purge automatique.

Il ne s'agit pas d'authentification IPC : Context reste construit par un hôte
trusted_in_process, aucun texte LLM/document ne crée une permission. Les contrats
UNIFIED, JNI, signatures, manifestes Android et protections ne sont pas modifiés.
Aucun moteur ni poids n'est chargé. Le snapshot historique Store n'exporte pas les
nouveaux namespaces : aucun transfert/migration de données utilisateur n'est livré.

## Utilisation

`JAVA_HOME=/workspace/.jarvis-core-tools/jdk-17.0.16+8 GRADLE_USER_HOME=/workspace/.services-tools/gradle-home ./scripts/cloud-gradle.sh :desktop:test :desktop:toolsDemo --no-daemon --max-workers=2`

La CLI crée son propre répertoire temporaire synthétique, cherche un document,
transforme un vrai CSV, exporte un DOCX après confirmation synthétique explicitée,
relit le DOCX et refuse le rejeu. L'hôte réel doit obtenir une décision utilisateur
sur le contenu exact avant ApprovalGate.confirm. Aucun outil ne permet de confirmer
sa propre invocation. ToolDispatcher.close termine l'admission de tâches mais ne
prétend pas que toutes les tâches ou ressources natives sont déjà libérées.

Les filtres CI couvrent les trois branches SERV, avec permissions contents:read
inchangées. Aucun succès CI distant n'est présumé à partir du push.

## Vérifications

Résultat final JVM : **69 tests, 0 échec, 0 erreur, 0 ignoré**, dont dix nouveaux
tests de dispatcher et les 59 historiques. Commande :desktop:toolsDemo réussie :
recherche SUCCESS, CSV SUCCESS, DOCX SUCCESS, relecture exacte et rejeu REFUSED.
Build Android/Lint lancé séparément, résultat encore en attente à ce commit ;
aucun test Android ni succès physique revendiqué.
Premier essai : échec environnement, JRE21 sans compilateur et cache utilisateur
non accessible. Utilisation du JDK17 complet déjà épinglé, Gradle8.13 et ses
vérifications de dépendances conservés. Premier passage des nouveaux tests : dix
échecs révélant un appel containsKey(null) incompatible avec Map.of ; défaut corrigé
sans désactiver de test. Les 59 tests historiques étaient passés.

## Limites et suite

Pas de raccordement CORE, Binder, génération native ou essai physique. L'expiration
est coopérative : une opération non coopérative n'est pas déclarée interrompue.
SERV-02 peut composer les adaptateurs après validation de cette livraison.
