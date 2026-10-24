# Utiliser l’atelier JARVIS SERVICES

L’atelier fonctionne sans CORE, LLM, NEXUS ou VOICE et sans accès réseau. Le bouton
« Diagnostic avancé et fonctions historiques » conserve l’accès au diagnostic
antérieur. Aucun appareil personnel n’est installé automatiquement.

## PDF → informations sourcées → DOCX

1. Choisir « PDF / consultation sourcée → DOCX ».
2. « Sélectionner un fichier local » ouvre le sélecteur Android. Sélectionner un PDF
   textuel (ou un document texte/DOCX pris en charge).
3. Saisir le texte à retrouver, ou laisser vide pour les premiers passages.
4. « Préparer et prévisualiser » présente les passages, pages, offsets et SHA-256
   de leurs sources. Une copie privée préserve les octets de l’original.
5. « Étape suivante / reprendre » vérifie les sources. Appuyer une seconde fois
   présente la confirmation du nom et du contenu exacts du nouveau DOCX privé.
6. Confirmer la création, puis choisir « Exporter dans un dossier choisi ».
   Le sélecteur Android accorde les droits sur le dossier volontairement choisi.
7. Vérifier le nom et le contenu présentés, puis confirmer l’export public. Le
   fichier créé est neuf; une relecture vérifie son empreinte avant le succès.

La synthèse est extractive et sourcée. Un PDF numérisé sans texte est refusé avec
`OCR_REQUIRED`. Le moteur n’effectue pas d’OCR ni de modification PDF en place.

## Modèles et modifications documentaires

Choisir « Remplir un modèle texte / DOCX », sélectionner un modèle contenant par
exemple `Bonjour {{name}}. Montant : {{amount}}.`, puis préparer. Un formulaire
présente les champs à remplir. Les champs absents, inconnus ou les marqueurs
mal formés sont refusés. Les valeurs sont du texte, jamais des instructions.

L’outil « Remplacer un texte → nouveau DOCX » applique un remplacement littéral
exact dans le texte extrait. « Créer un DOCX à partir du texte saisi » crée un
nouveau document à partir de la saisie. Ces opérations créent des documents
dérivés simples; elles ne conservent pas les styles, images, tableaux ou la mise
en page arbitraire d’un DOCX importé. Les originaux restent inchangés.

## CSV / JSON → nettoyage, calculs et export

1. Choisir « CSV / JSON → tableau et calculs », puis sélectionner le fichier.
2. Choisir nettoyage des espaces/Unicode et dédoublonnage exact si souhaités.
3. Facultatif : nom de colonne et valeur du filtre exact.
4. Facultatif : tri croissant et colonne du tri, distincte de la colonne calculée.
5. Pour les statistiques, renseigner la colonne numérique et son unité déclarée.
   Pour une somme par groupe, renseigner aussi la colonne de regroupement.
6. Choisir `csv` ou `json`, puis préparer. L’aperçu montre la sortie, les nombres de
   lignes, les différences positionnelles et des échantillons avant/après.
7. Vérifier et confirmer les deux publications comme pour le DOCX.

Les calculs ne dépendent pas d’un modèle. Les sommes/minimums/maximums sont exacts;
une moyenne non terminante est accompagnée de sa fraction exacte et d’une
approximation DECIMAL128 explicitement identifiée. Aucune unité n’est devinée ou
convertie implicitement. Les valeurs incompatibles avec le schéma sont refusées.

Formats qualifiés : CSV UTF-8, virgule, guillemets doubles échappés, lignes LF/CRLF;
JSON constitué d’un tableau non vide de lignes avec les mêmes clés et des valeurs
primitives. Les cellules exportées en JSON sont des chaînes pour conserver leur
représentation; les types sont contrôlés séparément par le schéma. Un JSON vide ne conserve pas les en-têtes d’un tableau vide et n’est
pas importable dans cette version. Pas de XLSX, séparateur CSV automatique ou
exécution de formule/script.

## Historique, redémarrage et annulation

L’historique permet de retrouver un aperçu et ses étapes. Les étapes déjà
terminées restent terminées après redémarrage. La destination publique doit être
choisie à nouveau si aucun export n’a commencé; aucun droit SAF n’est reconstruit
silencieusement. Une demande d’annulation est coopérative, notamment entre étapes
et blocs d’écriture; une opération native en cours peut finir avant de rendre la main.

`UNKNOWN` signifie que le résultat d’une opération n’est pas suffisamment prouvé.
L’application ne la rejoue pas automatiquement. Vérifier le dernier dossier
sélectionné et le journal avant une nouvelle opération explicite. Après échec ou
annulation, les originaux et les fichiers définitifs ne sont pas supprimés; seul
un document frais connu peut être supprimé pour rollback.

Limites : source de 2 MiB maximum, 100 pages PDF, 250 000 caractères dérivés,
5000 lignes et 100 colonnes, 64 jobs et 64 MiB de fichiers privés. La saturation du
journal ou d’un quota entraîne un refus explicite, sans purge des preuves.

## Mémoire et BRIDGE

« Mémoriser dans cet atelier » exige une confirmation du contenu et conserve une
préférence locale 30 jours. « Préparer un export de la mémoire locale » construit
un nouvel aperçu JSON à confirmer. La mémoire de cet atelier est distincte de
celle du diagnostic historique et d’une éventuelle mémoire personnelle centrale.
Aucun envoi ou écriture UNIFIED/NEXUS n’est possible ici.

Le protocole BRIDGE authentifié est fourni comme API autonome et démonstration de
transfert de fichiers synthétiques. L’appairage de production et le transport
entre appareils ne sont pas activés. Le simulateur historique reste accessible
au diagnostic avancé. Un ACK signifie réception des métadonnées, jamais permission
ou preuve d’exécution d’une commande distante.
