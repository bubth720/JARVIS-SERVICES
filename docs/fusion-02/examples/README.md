# Vrais fichiers issus du laboratoire Android

Les sources synthétiques et les sorties ci-jointes ont été relues dans le
provider privé de l'APK de fixture après15tests réussis sur l'émulateur API26.
Les tests ont utilisé le vrai DocumentsUI, des grants entre UID distincts et
ContentResolver pour importer/créer/fermer/relire les fichiers.

- source.csv garde les espaces/doublons ; result.csv contient deux lignes nettoyées,
  avec somme exacte0.30.
- source.pdf possède deux pages synthétiques ; source.txt est le TXT de contrôle.
- result.docx est le fichier réellement créé après consentement et relecture,
  avec le marqueur `FUSION24 exact synthetic conclusion`.

La copie de ces preuves vers le dépôt utilise ensuite `run-as` sur l'APK debug de
fixture synthétique. Ce transfert hôte ne remplace pas le test d'export SAF.
Les noms UUID d'origine, tailles et SHA sont dans ../evidence/android-files.json.
Aucune donnée utilisateur, migration ou installation personnelle.
