# Compatibilité et limites de responsabilité — 002A

ADR-001 relue intégralement depuis JARVIS-UNIFIED au SHA approuvé
`e0e6f69c4908a152da4e43864a08fef0c8f7a7af`, en lecture seule. Elle approuve
une orientation, pas les choix IPC ni une migration physique.

| Propriétaire | Frontière conservée en 002A |
|---|---|
| CORE | Décisions, identité de session, permissions et confirmations ; aucun nouvel orchestrateur de production SERVICES |
| NEXUS | Destination future des documents, connaissances et souvenirs confirmés ; aucun transfert, migration ou service de stockage concurrent publié |
| LLM | Moteur et catalogue de réponses existants ; aucun catalogue SERVICES et aucun modèle local ajouté |
| SERVICES | Fonctions réutilisables et diagnostic autonome isolé, mocks explicitement étiquetés |

Point de chevauchement existant : KNOWLEDGE et MEMORY stockent des données dans
la sandbox du diagnostic FOUNDATION. Cela ne qualifie pas SERVICES comme autorité
centrale de connaissances ou souvenirs. Les confirmations locales de diagnostic
simulent l'hôte de confiance et ne deviennent jamais une permission CORE implicite.
002A qualifie cette sandbox sans ajouter de fonctionnalité concurrente ni modifier les contrats. Le seul correctif métier concerne la lecture XML DOCX de DOCUMENTS, dont un échec natif Android 26 a été démontré ; aucune nouvelle responsabilité n’est introduite. Le raccordement et la délégation vers NEXUS seront
à spécifier/autoriser séparément. Aucun accès à un catalogue LLM n'est créé.

## BRIDGE : perspective PC / téléphone / montre

Exigences préparatoires uniquement, sans nouveau transport ou protocole de sync :
chaque appareil continue hors ligne ; les échanges sont explicites, authentifiés et
minimaux. CORE autorise pairs et opérations, NEXUS reste propriétaire de la politique
de stockage et résolution des conflits, BRIDGE transporte des messages/références.
Prévoir identifiants stables, versions de schéma, révisions, accusés de réception,
déduplication et reprise après déconnexion. Un envoi rejoué ne doit jamais rejouer
une action sensible. Aucun broadcast de documents, mémoire privée ou catalogue LLM.

Les choix de chiffrement/identité, consentement par appareil, file d'attente durable,
politique de conflit et format de synchronisation restent ouverts et nécessitent
une mission dédiée. Le simulateur echo actuel ne prouve aucune synchronisation ni
compatibilité avec une montre réelle. Ni la montre ni le téléphone n'ont besoin
d'une connexion cloud pour les fonctions locales. Aucun pilote matériel ajouté.
