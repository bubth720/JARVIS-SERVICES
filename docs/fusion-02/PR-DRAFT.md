FUSION-02 SERVICES : proposition de fence et qualification SAF isolée

Le port API24 conservait un instantané de permissions et refusait tout export SAF.
Cette branche propose une frontière d'engagement explicite détenue par CORE et
la teste avec une autorité de laboratoire. L'export CSV/DOCX réel est limité à
l'APK AndroidTest ; le port de production garde SAF_EXPORT_UNQUALIFIED.

Le journal du SDK prend désormais son verrou avant l'ouverture SQLite et refuse
la corruption sans utiliser le gestionnaire Android susceptible de supprimer les
reçus. Le POM parent Guava manquant est vérifié et ajouté au verrou SHA256.
Les jobs historiques sont conservés et la CI ajoute la matrice native24/25/26/35.

Validation locale :100 tests JVM (94 historiques conservés),40 Robolectric et15 tests instrumentés par API24/25/26. Les129 tests de la référence FINAL17 ont été relancés séparément. API35, CI exacte, simulations et limites sont distinguées dans le rapport.

Les résultats, échecs initiaux, limites et preuves sont dans docs/fusion-02/REPORT.md,
VALIDATION.json et PROVENANCE.json. La branche finale à17outils est conservée
séparément ; sa comparaison ne vaut pas intégration dans le SDK API24.

Revue requise CORE/UNIFIED avant changement de l'autorité réelle ou activation
runtime. Aucune fusion, activation IPC/réseau, donnée personnelle ni installation
sur appareil utilisateur. Cette PR doit rester en brouillon.

Refs bubth720/JARVIS-SERVICES#3, bubth720/JARVIS-UNIFIED#5.
