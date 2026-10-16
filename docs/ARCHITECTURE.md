# Architecture et contrats SERVICES 1.0.0

Le métier Java 11 n'importe ni Android, ni JDBC, ni moteur LLM. Les modules
communiquent via des appels typés en processus ; aucune socket, IPC Binder, serveur
HTTP ou API cloud n'est implémenté. Java est partagé entre JVM et APK, sans Python.

`shared/api/Runtime` compose les huit modules ; `Store` possède les tables, `Database`
isole SQL et transactions, `Documents.PdfText` isole le parseur PDF. Les originaux
sont immuables, identifiés par UUID ; SHA-256 lie texte, extraction et provenance.
`adapters/llm/LlmPort` produit une sortie structurée avec citations, disponibilité,
arrêt et preuve de fin. Les implémentations livrées sont un mock déterministe et un
adaptateur indisponible, sans moteur. `adapters/core/CoreAdapter` est une façade de
simulation interne, limitée à health/search ; elle n'est pas un raccordement CORE.

## Contrats

`contracts/v1` contient requête, réponse, progression, annulation et capacités.
L'enveloppe SERVICES n'est PAS l'enveloppe fermée UNIFIED. Pas d'ajout aux schémas
UNIFIED et pas de traduction automatique vers IPC. Les opérations métier complètes
sont accessibles aux hôtes locaux par les API Java, pas toutes via CoreAdapter.
La validation des constructeurs est active ; les schémas décrivent la sérialisation,
mais aucun décodeur réseau authentifié n'est livré. Ne pas désérialiser ces objets
avec Gson pour leur conférer des droits.

`permission_context.transport=trusted_in_process` signale une référence hors bande :
le véritable `Guard.Context` est créé par l'hôte de confiance puis fourni séparément.
Un JSON, un document ou une sortie LLM ne peut pas créer une autorisation. La façade
retourne une réponse corrélée et une erreur structurée, sans message privé en logs.
`Task` possède une identité interne, une échéance monotone, progression ordonnée et
annulation coopérative. L'hôte conserve la relation requête/tâche ; l'annulation est
idempotente. Le bail lourd reste acquis jusqu'au retour effectif de l'appel.

Les versions incompatibles sont refusées ; le futur transport devra négocier ses
versions, authentifier l'appelant et borner les messages avant désérialisation.
CORE devra rester l'autorité finale. Aucun transport final n'est choisi ici.

## Modules

DOCUMENTS extrait TXT/MD/CSV/JSON, PDF textuels et DOCX ; recherche, comparaison,
résumé extractif référencé et export DOCX/TXT/MD/CSV/JSON (contenu fourni par l'hôte).
Les sections sont des lignes avec offsets, pas une analyse typographique PDF. La recherche documentaire renvoie au plus 50 résultats avec extrait de 300 caractères, identifiant et révision, pas les corps complets.
Les PDF sans texte demandent explicitement OCR ; aucune extension XLSX implicite.

KNOWLEDGE indexe par hash/version, FTS5 exact, classement BM25, suppression/reconstruction
et cosinus de vecteurs locaux 64 dimensions. `Embeddings` est interchangeable ; le
modèle de concepts FR/EN livré ne couvre que 12 groupes explicites de synonymes,
avec hash lexical en repli et collisions possibles. Il ne remplace pas un encodeur
sémantique qualifié. `ZimArchive` est un port, pas un lecteur ZIM opérationnel.

DATA traite les chaînes CSV/JSON en tableaux bornés : validation de forme et nombres,
filtre, tri, dédoublonnage exact, normalisation NFC/trim, agrégations décimales,
statistiques et historique. Sommes exactes BigDecimal ; moyenne en DECIMAL128,
précision annoncée. Aucun calcul confié au modèle.

MEMORY distingue carte volatile et souvenirs persistants (projet/tâche/décision/
préférence), provenance, date, version, expiration, correction, suppression/export.
Le contenu exact nécessite un jeton de confirmation pour chaque sauvegarde durable.
La rétention masque immédiatement les expirés ; la purge est explicite, pas un daemon.

ACTIONS accepte uniquement `document.export`. Chaque workflow borné nécessite une
confirmation de l'identité et des étapes exactes ; journal et résultats persistés.
Pas de shell, d'exécution de code ni de registre modifiable par LLM. Un crash après
lancement devient `unknown` lors de la récupération ; aucun rejeu automatique. Pour
reprendre, vérifier les sorties existantes et proposer un NOUVEAU workflow confirmé.
Les étapes déjà réussies restent conservées après l'échec d'une étape ultérieure.

ANALYST assemble des extraits, différences, conflits candidats clé/valeur, calculs
et interprétation séparés. Chaque citation du mock/port doit correspondre à une
source et une révision connues, avec citation littérale réelle. La couverture
citée est quantifiée ; aucune confiance probabiliste arbitraire. Une différence
ou un conflit de champs ne prouve pas une contradiction factuelle. La sélection manuelle et la récupération FTS5 sont livrées. `analyze` exécute un plan fixe et inspectable : recherche, preuves, comparaison, interprétation, puis calcul décimal optionnel d'une table fournie explicitement par l'hôte. La planification LLM générale reste à qualifier.

GUARD impose des grants outil, racine de fichiers privée, anti-traversée/liens
symboliques, confirmations éphémères à usage unique et audit borné sans contenu.
BRIDGE est une interface de transport local avec découverte/grants par pair,
capacités, corrélation et version. Seul un pair echo simulé est livré, sans pilote
matériel ni découverte réseau automatique.

## Ressources

Les parcours du corpus documentaire, vectoriel, mémoire et de réindexation utilisent un curseur par lots de huit entrées, sans décoder tout le corpus à la fois.

Un unique bail lourd statique par processus couvre extraction, index, recherche
vectorielle, génération et récupération. Il ne constitue pas un arbitre global
CORE/LLM inter-applications. Les tâches sont synchrones et coopératives : une
bibliothèque native bloquée n'est pas tuée arbitrairement. L'hôte Android vérifie
RAM/batterie/température avant les travaux, annule à la sortie du premier plan,
utilise un seul worker et refuse l'empilement. ECO/AUTO : import 512 Kio ; BALANCED :
2 Mio. AUTO conserve volontairement le repli ECO. Aucun mode pleine puissance
n'est activé et aucune qualification thermique physique n'est revendiquée.
