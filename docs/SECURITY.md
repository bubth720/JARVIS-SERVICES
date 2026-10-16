# Sécurité et frontières de confiance

Le diagnostic est autonome et non exporté comme service. L'Activity de lancement
est accessible au launcher ; aucun composant n'accepte un appel métier provenant
d'une autre application. L'APK applicatif ne demande pas INTERNET ni l'accès global
au stockage. SAF limite les imports/exports au choix de l'utilisateur. `allowBackup`
est désactivé pour éviter les sauvegardes automatiques de données privées.

Un hôte local de confiance attribue les grants `Guard.Context`. Ce modèle n'est
pas une authentification IPC. Le LLM retourne des données/propositions, jamais
une autorisation. Les instructions dans les documents restent du texte ; aucun
interpréteur de commande n'existe. Le catalogue ACTIONS est fermé.

Les confirmations sont liées à l'identité, opération et SHA-256 du contenu exact,
expirent en 60 secondes et sont consommées une seule fois, y compris après refus.
Seule l'UI/l'hôte de confiance appelle `confirm` après accord explicite. Ne jamais
exposer ce constructeur de confirmations au modèle ou à un décodeur de messages.
Un maximum de 128 confirmations attendues est imposé.

Les fichiers sont dans une racine privée de l'application. Les chemins absolus,
traversées et liens symboliques sont refusés ; les écritures utilisent CREATE_NEW.
Ce contrôle suppose qu'un processus malveillant n'a pas déjà un accès en écriture
au répertoire privé. Il n'offre pas de sandbox OS face à un attaquant avec le même
UID, root ou une course de remplacement de répertoires. Pas de fichiers arbitraires
sur le stockage partagé ; aucun écrasement d'original n'est proposé.

DOCX utilise ZIP/XML avec taille extraite bornée, DTD et entités externes refusées.
Les imports sont bornés à 2 Mio, texte 250 000 caractères, PDF 100 pages, corpus
128 documents. CSV : 5 000 lignes et 100 colonnes. L'audit conserve 500 événements
sans contenu, prompt ou texte privé. Le rapport technique exporte uniquement ces
événements de code/statut, les versions, capacités et limites.

Ces limites réduisent la consommation ; elles ne prouvent pas la résistance de
PDFBox aux documents hostiles ni l'absence de faille native. Le temps maximal est
coopératif, pas une interruption forcée des parseurs. Les erreurs UI ne divulguent
pas de chemins ou contenus. Réexaminer les dépendances avant diffusion, puis tester
sur les appareils cibles. Les APK debug servent au diagnostic, sans signature de
production ni déploiement autorisé.

La sauvegarde Recovery contient les données privées en clair ; elle reste sous
contrôle explicite de l'hôte. Elle n'est pas exportée par le bouton de diagnostic.
Elle est distincte du format de sauvegarde UNIFIED et n'est pas chiffrée.
