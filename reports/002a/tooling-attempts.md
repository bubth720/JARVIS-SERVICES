# Tentatives d'outillage réellement observées

1. Build/régression initial : 55 JVM, assemblage des deux APK et Lint réussis.
   Voir baseline-build.log et baseline-validation.json. Aucun test Android n'était
   présenté comme exécuté à ce stade.
2. Téléchargement Google vérifié (SHA-1 du manifeste officiel via HTTPS, SHA-256
   enregistré) : emulator37.3.4, platform-tools37.0.1, AOSP API26 x86. Voir
   sdk-provenance.json. Aucun contrôle TLS/empreinte désactivé.
3. Premier lancement `-accel off` : refus explicite de l'émulateur37, qui demande
   `ANDROID_I_WANT_MY_TCG=yes` pour son moteur logiciel. Voir emulator-boot.log.
   Le second lancement avec cet indicateur a démarré ; les propriétés qemu=1,
   SDK=26, boot_completed=1 sont enregistrées dans emulator-readiness.log.
   Aucune modification de /dev/kvm, permission système, protection thermique ou batterie.
4. SDK adb37 a échoué avec `Cannot mkdir '/home/agent/.android': Read-only file system`,
   y compris avec ANDROID_USER_HOME/ANDROID_SDK_HOME. Aucun HOME réaffecté ni droit
   élevé demandé. Le client de protocole ADB local adb-shell0.4.4 a été installé
   dans /tmp ; voir adb-client-install.log et la procédure documentée.
5. Première tentative ADB : délai transport de 30 secondes pendant l'installation,
   avant tout cas JUnit. original-three-instrumented.json conserve cet échec.
   Après diagnostic, délai transport porté à 300 secondes, sans changer les délais
   métier. original-three-retry.log/json : trois tests Android réellement réussis.
6. Première campagne étendue : neuf succès / un échec DOCX (PROCESSING_ERROR).
   expanded-instrumentation-first.log/json. Diagnostic ciblé : zéro succès / un
   échec, ParserConfigurationException sur disallow-doctype-decl du parseur Harmony.
   docx-diagnosis.log/json contient la preuve native, pas une supposition de JVM.
7. Remplacement du parseur DOCX par SAX borné, DTD et entités refusées par callbacks
   obligatoires. Quatre nouveaux tests JVM et un nouveau test Android d'encodage
   hostile. docx-fix-build.log : 59 JVM et build/lint réussis.
   instrumentation-after-fix.log/json : 11 succès, zéro échec/saut.
8. Reconstruction finale avec toutes les tâches réexécutées : final-build.log.
   Seconde campagne indépendante sur les APK finaux : instrumentation-final.log/json.

L'APK candidat utilisé par les premières campagnes comportait déjà les correctifs
UI/backup et les nouvelles fixtures, malgré son répertoire temporaire nommé
baseline-002a. Ce nom n'est pas une preuve de binaire FOUNDATION non modifié.
La base n'a été qualifiée que par le build/JVM initial ; les SHA des APK exécutés
sont enregistrés dans chaque résultat d'instrumentation.

L'API GitHub Actions a été refusée au niveau du tunnel proxy (HTTP403), avant
validation d'une autorisation GitHub. Le token éventuellement injecté n'a pas été
imprimé et aucun token n'a été demandé. Les opérations Git du dépôt fonctionnent.
Cette erreur ne prouve ni un échec ni un succès de la CI distante. Aucun contournement
de politique réseau n'a été tenté. Voir ci-initial.json.
