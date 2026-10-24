# SERVICES-FINAL-01 — responsabilités et contrats locaux v1

Base exclusive : `3decdc47d82f9b43713c48cab244762941770a81` (SERV-03).
Les huit domaines et les trois outils historiques sont conservés. Les codecs
UNIFIED vendoriés restent limités à desktop et inchangés; vérifier avec
`python3 scripts/check-services-contract-lock.py`.

L'autorité reste le host de confiance : CORE dans une intégration future, ou
l'utilisateur de l'interface autonome pour ses opérations locales. Aucun texte de
modèle, document importé, champ JSON ou paquet BRIDGE ne peut fabriquer un grant,
un `Guard.Context`, un adaptateur exécutable ou une confirmation. Le catalogue
n'est pas une délégation d'autorité. Les outils sensibles exigent une approbation
à usage unique liée à la version, aux arguments, à l'appel et au principal.

## Huit domaines

| Domaine | Fonction locale finalisée | Frontière |
| --- | --- | --- |
| DOCUMENTS | Lecture PDF paginée/DOCX/texte, extrait ciblé, modèle `{{champ}}`, remplacement littéral, nouveau DOCX | Pas d'OCR ni de modification PDF en place; pas de conservation de la mise en page d'un DOCX importé |
| KNOWLEDGE | Consultation des seules sources sélectionnées, passages/offsets/pages/empreintes vérifiés | Adaptateur `ExistingKnowledgeAdapter` vers le contrat lexical existant; port optionnel externe, aucun nouveau corpus central NEXUS |
| DATA | CSV/JSON tabulaire, nettoyage, dédoublonnage, filtre exact, tri croissant texte/numérique, regroupement/somme, schéma et unités déclarées, aperçu avant/après | Aucune exécution de formule, script, XLSX ou conversion d'unité implicite |
| ACTIONS | Catalogue versionné, workflows vérification/publication, confirmations exactes, annulation coopérative, journal append-only | Une opération incertaine reste bloquée; aucune répétition automatique |
| MEMORY | Lecture, écriture confirmée et export de la mémoire de l'atelier | Aucune mémoire personnelle centrale, aucune écriture UNIFIED/NEXUS; une intégration future nécessite un contrat approuvé et une autorisation externe |
| ANALYST | Statistiques décimales, comparaison de sommes à unités égales, diagnostics de types sourcés, rapport de calcul | Moyenne non terminante indiquée comme approximation DECIMAL128 avec fraction exacte; interprétation/hypothèses séparées et vides par défaut |
| GUARD | Permissions, références bornées, chemins privés, refus des liens symboliques et traversées, quotas, preuves d'exécution | L'espace privé Android/JVM est supposé protégé contre un autre code du même UID; aucune sandbox OS nouvelle n'est revendiquée |
| BRIDGE | Protocole de fichiers authentifiés HMAC-SHA256, séquences persistantes, déduplication et ACK authentifiés | Aucun transport de production, socket, découverte, appairage automatique ou exécution distante |

## Contrats et versions

`contracts/services-final/v1/` décrit les arguments et les trames du protocole
SERVICES local. Ces fichiers ne modifient aucun schéma UNIFIED et ne revendiquent
pas de compatibilité IPC supplémentaire. La version de chaque outil est `1.0.0`.
Les arguments sont un objet exact de chaînes; les recettes/schémas JSON sont des
chaînes structurées vérifiées avec refus des clés dupliquées et des opérateurs
inconnus. Le maximum cumulé des arguments reste 2 MiB.

Recette DATA v1 : liste de 16 opérations maximum parmi `NORMALIZE`, `DEDUPLICATE`,
`FILTER`, `SORT_TEXT`, `SORT_NUMERIC`, `STATS`, `AGGREGATE`. `FILTER` compare une
valeur littérale; les tris sont croissants. `AGGREGATE.column` est le groupe et
`AGGREGATE.value` la colonne décimale à sommer. Le schéma associe chaque colonne
contrôlée à `TEXT`, `INTEGER`, `DECIMAL`, `BOOLEAN` ou `DATE`, et à une unité
explicitement déclarée parmi `""`, `1`, `EUR`, `USD`, `kg`, `g`, `m`, `cm`, `s`,
`ms`, `%`. Une déclaration ne constitue pas une conversion ou une détection.

Les transformations de nettoyage précèdent la validation du tableau final.
`analyst.table.inspect` diagnostique aussi un tableau brut sans en recopier les
valeurs invalides : ligne, colonne, code, empreinte de source et limite de 100
incohérences. Les différences d'aperçu sont positionnelles, avec échantillons
avant/après de 20 lignes et 100 indices maximum; elles ne prétendent pas être un
alignement sémantique de lignes après tri.

## Persistance et incertitude

L'atelier conserve une copie privée de la source, son empreinte et un aperçu
dérivé immuable. La définition de workflow est liée au plan et aux références.
Le plan DATA et ses métadonnées sont enregistrés dans la même transaction initiale.
Une vérification achevée n'est pas rejouée après redémarrage. Une étape `RUNNING`
récupérée devient `UNKNOWN`. L'export public est lui aussi journalisé avant la
première création; un résultat de création/commit non prouvé ne revient jamais à
`PENDING`. La destination doit être choisie par l'utilisateur et confirmée après
présentation du nom et du contenu exacts. Le provider doit créer un document neuf,
et sa relecture doit donner le même SHA-256. Seul ce document neuf peut être
supprimé pour rollback. Le dispatcher reste conservateur (`UNKNOWN`) après une
exception d'action, même lorsqu'un rollback ultérieur est confirmé `FAILED` dans
le journal de publication.

Les nouveaux journaux sont bornés et non purgés automatiquement : 64 jobs,
64 MiB de fichiers privés, 4096 événements d'exécution, et bornes SERV-03 conservées
pour workflows/approbations. La saturation refuse de nouvelles opérations.
Ils ne sont pas ajoutés au snapshot historique `Recovery` : la sauvegarde ou
migration de ces journaux n'est pas revendiquée. Les refus GUARD historiques
restent aussi visibles dans l'audit borné; le journal de l'atelier conserve les
preuves d'appels admis sans arguments bruts ni contenu documentaire.

## BRIDGE : proposition autonome, sans activation réseau

`OfflineBridge` est une API de codec/files durables, distincte du transport
historique désactivé et du simulateur d'écho conservés. Le host fournit une clé
aléatoire partagée de 32 à 64 octets par paire et un identifiant de session UUID;
le protocole ne distribue ni ne découvre les clés. La clé reste en RAM; seul son
fingerprint est conservé pour refuser une substitution dans une session existante.
La perte de clé nécessite une nouvelle procédure d'appairage hors de cette mission.

Les trames sont du JSON canonique trié signé avec séparation de domaine
`services-offline-1`. Identités, session, identifiant, numéro de séquence, type et
référence opaque sont authentifiés; l'ACK lie l'identifiant et l'empreinte exacte
de la trame reçue. Le récepteur refuse une mauvaise clé, identité/session, séquence
ou structure. Une retransmission identique rend le même ACK sans nouvel effet.
Seuls `document.reference` et `diagnostic.ping` sont admis; ni l'un ni l'autre ne
peut déclencher une action. `tool.invoke` et toute autre commande sont refusés.
Le contenu d'un fichier ou d'une mémoire ne transite pas dans cette version.

Transport étudié et qualifié en laboratoire : transfert volontaire d'un fichier
puis d'un reçu. Des canaux USB/local radio explicitement autorisés pourraient
porter ces mêmes octets après une qualification distincte. Aucun n'est implémenté
ou activé en production ici; aucune permission INTERNET n'est ajoutée au manifeste.
