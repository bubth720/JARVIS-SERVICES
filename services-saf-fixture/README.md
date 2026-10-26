# FUSION-02 — provider SAF de laboratoire

APK `testOnly`, minSdk24, sans permission réseau ni lancement automatique.
Il ne contient que des fichiers synthétiques et un DocumentsProvider protégé par
MANAGE_DOCUMENTS. Le sélecteur Android délivre les grants au consommateur de test,
qui possède un autre UID. Aucune sélection de fichier utilisateur n'est automatisée.

Le contrôle normal/corrupt/deny est une injection de fautes par receiver explicite,
protégé par permission signature. Seul l'APK AndroidTest SERVICES demande cette
permission ; aucun composant/permission n'est ajouté à l'AAR de production.
Ne pas distribuer cet APK ni l'installer sur un appareil personnel. Le runner
cloud vérifie localhost/qemu/API avant installation. Voir docs/fusion-02.
