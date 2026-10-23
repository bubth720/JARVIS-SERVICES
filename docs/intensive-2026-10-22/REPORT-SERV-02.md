# SERV-02 — pipeline documentaire et provenance

Branche : `feat/serv-02-services`, issue de SERV-01
`d300642a4f10b53bdc988a43e0d7abb05c0d2253`. Référence de fiche :
`67e7f798b8bbcb8391e0d57ea5b886a2a9edf4db`, code historique `0f462c8...`.

## Capacités livrées

- `DocumentTextExtractor.extract(name, bytes, Task)` retourne des pages immuables
  avec identité SHA-256 du fichier, SHA-256 du texte, page PDF réelle (1-based),
  ou page 0 explicitement non paginée. Lecture seule, aucun Store ni import.
- PdfBoxText (JVM PDFBox 2.0.33) et AndroidPdfText (PDFBox Android existant)
  implémentent le port PdfPages, sans changer la méthode historique extract.
  UTF-8 strict pour texte/Markdown/CSV et lecteur DOCX XML sécurisé existant.
  Bornes conservées : 2MiB, 100 pages, 250000 caractères.
- Pipeline fermé EXTRACT/NORMALIZE/SEGMENT/TRANSFORM/REPORT, source et recette
  bornées, progrès/cancellation via Task existant. Les citations et offsets UTF-16
  restent sur le texte brut ; la normalisation NFC est une projection séparée.
- Opérateurs CSV existants réutilisés : normalisation, dédoublonnage, filtre, tri,
  statistiques et agrégation décimales exactes. Aucun script, LLM ou moteur neuf.
- Rapport source/page/extrait exact vérifiable, SHA fichier et texte ; aucune
  citation d'une paraphrase. Le rapport se déclare déterministe, non génératif.
- Export privé local Markdown/DOCX/TXT après confirmation exacte durable : fichier
  temporaire vérifié puis publication hard-link atomique sans écrasement. Si le
  système de fichiers ne le supporte pas, refus explicite. Les originaux ne sont
  jamais éligibles au nettoyage, uniquement le temporaire frais.

Le port d'extraction JVM est utilisable par NEXUS comme dépendance de bibliothèque
épinglée, sans partage des tables SERVICES. API :
`fr.jarvis.services.pipeline.DocumentTextExtractor` et
`fr.jarvis.services.platform.PdfBoxText`, modules `shared-runtime` et `desktop`.
Versions : Java11, Gson2.11.0, PDFBox2.0.33 ; SQLiteJDBC3.46.1.0 pour la CLI/tests.

## Validation réellement exécutée

- `./scripts/cloud-gradle.sh :desktop:test --no-daemon --max-workers=2` :
  **84 tests, 0 échec, 0 erreur, 0 ignoré** (59 historiques, 10 SERV-01,
  8 pipeline, 7 protocole SAF).
- Vrais PDF à deux pages + CSV synthétiques : citations, somme exacte0.30,
  Markdown et DOCX relus, corruption/XXE, UTF-8 invalide, cancellation, provenance,
  refus sans permission, non-écrasement et rejeu testés.
- `-PwithAndroid :android:assembleDebug :android:assembleAndroidTest :android:lintDebug`
  réussis : log daemon-439990.out.log, BUILD SUCCESSFUL, 69 tâches exécutées,
  22 octobre 2026. Lint : **0 erreur, 1 avertissement** TrustAllX509TrustManager
  dans bcpkix1.86 déjà dépendance historique. Pas de suppression de Lint.
- Commandes lancées avec JDK17 complet, Gradle8.13 vérifié, SDK35 existant,
  caches et ANDROID_USER_HOME hors dépôt. Premiers échecs d'environnement
  (JRE sans javac, home Android en lecture seule) corrigés sans réduire les tests.
- Aucun test instrumenté exécuté dans cet environnement ; compiler son APK ne
  constitue pas une exécution. Aucun appareil, modèle, JNI, audio ou profil privé.

## SAF livré et qualification distincte

SafExportWriter et SafUriPort ajoutent un protocole de document frais : cible
opaque liée à la sélection hôte, confirmation exacte incluant la requête, fichier
privé temporaire encodé par Documents existant, écriture, relecture SHA-256 puis
publication logique. SelectedTreeExportPort Android exige un arbre content://
explicitement choisi et les grants lecture/écriture déjà présents ; il n'obtient
aucune permission. Les identifiants préexistants sont recensés et jamais ouverts
pour écriture ou suppression. Le nom final inclut le UUID de requête, affichable
par destinationName avant confirmation.

Un échec supprime uniquement le document fraîchement créé si le provider confirme
ce rollback ; perte d'ACK de création, nettoyage incertain ou effet final ambigu
conservent UNKNOWN et interdisent un succès/rejeu implicite. Cette transaction par
rollback **ne promet pas une visibilité atomique SAF** : un document partiel peut
être visible avant validation. L'export privé hard-link, lui, reste atomique.

Les sept tests JVM du protocole utilisent des doubles explicites de provider :
relecture, DOCX réel, corruption, rollback, perte d'ACK, autorisation et annulation.
L'adaptateur Android et trois tests d'instrumentation sont compilés ; aucun provider
SAF Android n'a été exécuté dans cet environnement. La qualification de terrain
reste ouverte. Aucun test double n'est présenté comme une preuve SAF Android.
Le code logiciel est livré ; disponibilité opérationnelle Android non présumée.

Dernière commande combinée -PwithAndroid :desktop:test :android:assembleDebug
:android:assembleAndroidTest :android:lintDebug : BUILD SUCCESSFUL, 72 tâches,
23 octobre 2026, log /workspace/.services-tools/serv02-final.log. Lint reste à
0 erreur et 1 avertissement tiers ; aucun seuil abaissé.

## Démonstration

`./scripts/cloud-gradle.sh :desktop:pipelineDemo --no-daemon --max-workers=2`
crée son propre PDF et CSV synthétiques, exécute le pipeline, exporte Markdown et
DOCX, puis vérifie le DOCX. Les confirmations de cette démo portent seulement sur
ces données générées ; un hôte utilisateur doit obtenir la décision réelle.

## Suite

Qualifier le provider SAF Android réel sur un environnement autorisé. SERV-03
peut employer les capacités privées et de lecture testées ; le parcours SAF ne
devient pas qualifié par sa compilation seule.
Aucun main fusionné, permission, signature, migration, JNI ou budget modifié.
