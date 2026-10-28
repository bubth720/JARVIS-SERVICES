Le lot3 ajoute un banc déterministe de qualification des reçus dans les sources de test uniquement : décision unique, horloge injectable, journal mémoire, révocation/annulation/expiration, commandes doublonnées et récupération UNKNOWN sans retry. La production et son refus SAF_EXPORT_UNQUALIFIED restent inchangés.

Validation locale : 121 tests JVM (107 préservés +14 nouveaux), 40 tests Robolectric, zéro échec/erreur/saut ; AAR/APK androidTest adaptateur et Lint réussis ; compilation de24 sources contre API24 réussie. Les quatre campagnes Android de15 cas chacune restent exigées par CI au SHA publié et ne sont pas prétendues exécutées localement.

ADR-002 demeure NON APPROUVÉE. Aucun raccordement CORE, aucune migration, aucune permission/signature/dépendance nouvelle. Rapport, commandes et preuves : docs/prefusion/lot3/REPORT.md et EVIDENCE.json.

PR brouillon vers mission/prefusion-lot3-20261027 uniquement ; aucune fusion demandée dans main.
