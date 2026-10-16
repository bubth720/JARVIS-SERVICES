# Stockage et récupération

SQLite contient `schema_meta`, `entries(namespace,id,body)` et `search` (FTS5).
Les modules n'obtiennent pas de handle SQL : Store fournit transactions, index et
repos nommés. Les adaptateurs JDBC/JVM et AndroidX bundled implémentent Database.
Le moteur Android embarqué évite de présumer FTS5 disponible dans SQLite du fabricant.
Les originaux sont des fichiers immuables UUID, avec SHA-256 dans les métadonnées.

La migration 0 → 1 est transactionnelle et idempotente. 1 → 0 n'est autorisée
que lorsque les données sont vides : autrement `BACKUP_REQUIRED`, sans suppression.
Une version future inconnue est refusée. Ce sont les données SERVICES uniquement,
aucune migration legacy CORE/LLM/NEXUS n'est effectuée.

Recovery sauvegarde un snapshot logique versionné avec SHA-256 et les originaux
vérifiés en Base64. Les exports dérivés DOCX ne sont pas inclus ; ils restent des
sorties indépendantes. Taille des originaux sauvegardés : 16 Mio, restauration
32 Mio de JSON maximum. Les corpus plus volumineux nécessiteront un format en flux.
Le package n'est ni chiffré ni compatible JVBK/JVIENC01.

L'hôte doit accorder `storage.backup` / `storage.restore`. La restauration exige
une confirmation du SHA-256 du package exact. Elle valide version, snapshot,
chemins et empreintes des originaux avant remplacement transactionnel des
métadonnées et reconstruction FTS. Les originaux existants différents sont
refusés. Les nouveaux fichiers sont supprimés si l'opération échoue avant commit.
Une panne du processus pendant la copie peut laisser des originaux orphelins,
mais ne doit pas remplacer des fichiers existants ; inspection avant nouvelle
restauration. Conserver une sauvegarde indépendante avant toute restauration réelle.

Le snapshot Store est une primitive interne réservée à Recovery/hôte de confiance,
pas une API utilisateur sans permissions. Les tests couvrent intégrité SQLite,
rollback, migrations, snapshot/index, originaux et package corrompu. Un test de
coupure électrique physique et la récupération sur appareil restent non exécutés.
