# Exemples sur fichiers réels, données synthétiques

- `source.pdf` → `result.docx` : montant et provenance SHA-256 dans un DOCX relu.
- `source.csv` → `result.csv` : nettoyage/dédoublonnage, deux lignes, somme exacte `0.30`.
- `scenarios.json` : preuves des deux processus JVM, aperçu, calculs, refus et BRIDGE.
- `benchmarks.json` : mesures JVM sur CSV 100/1000/5000 lignes et PDF 1/10/50 pages.

Les noms UUID des sorties de `scenarios.json` sont ceux des fichiers réellement
produits. Les copies fournies ici sont renommées `result.docx` et `result.csv`;
leurs SHA-256 correspondent à `docx_output.sha256` et `csv_output.sha256`.
Les fichiers contiennent des données synthétiques et sont de vrais fichiers
PDF/OOXML/CSV, pas des objets de simulation.

Reproduire depuis la racine avec un JDK complet et les dépendances disponibles :

```sh
./gradlew :desktop:finalScenariosDemo --no-daemon
```

Le chemin `FINAL_EXAMPLE_DIRECTORY` contient les entrées, sorties et rapports de
la nouvelle exécution. La démonstration utilise deux processus JVM distincts,
avec sortie propre entre étapes; le test Android séparé couvre un force-stop réel.
Les confirmations de la démonstration sont des consentements de fixtures
synthétiques, pas des autorisations utilisateur réutilisables en production.
Les mesures sont une passe par fichier, sur JVM Linux; aucun gain énergétique ni
qualification de matériel Android n’est déduit. Les PDF générés et archives DOCX
peuvent avoir des métadonnées variables; les contenus et conclusions sont vérifiés
à chaque exécution et liés aux empreintes réellement obtenues.
