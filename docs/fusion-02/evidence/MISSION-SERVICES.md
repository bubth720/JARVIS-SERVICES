# Mission FUSION-02 / SERVICES — Outils Android qualifiés et sûrs

**But prioritaire :** qualifier de vraies opérations Android API24/25 et corriger le défaut d'architecture révélé par CORE FUSION-01 : autorisation révoquée pendant qu'un worker a déjà commencé une mutation.

### Sources réelles
- `feat/serv-04a-android-tool-port` `f00091a63961`, parent SERV-03 `3decdc47d82f`, 94 JVM/40 Robolectric, AAR min24. `ServicesAndroidToolPort` permet lecture TXT/PDF 2 pages, recherche ciblée, CSV ; **DOCX export reste SAF_EXPORT_UNQUALIFIED**, sans tests Android instrumentés.
- `feat/services-final-01` `d16dd84f2788` : 17 outils,129 JVM/17 instrumentés émulateur API26 dans l'app autonome ; branche distincte non composée dans le SDK API24.
- CORE FUSION-01 `36761cea101b` : l'ancien Guard.Context est un instantané ; le host revalide un grant à la confirmation, mais **l'autorisation n'est pas atomiquement linéarisée avec le début de l'écriture**. Les tests refuseront l'acceptation tardive, sans empêcher forcément le fichier créé.
- CI SERV-04A GitHub run `40115305244` échoue dans la qualification API24 ; lire cause précise si logs accessibles.

### Phases prioritaires
**S0 audit/réparation de CI** : reproduire les suites, préserver 94 JVM+40 Robolectric, vérifier modules portables et SDK min24, sorties API24, hypothèses `ContentResolver`/PDFBox, licence/empreintes. Corriger workflow et prouver la CI exacte sans neutraliser tests.

**S1 protocole transactionnel & révocation :** co-concevoir avec CORE un **point d'engagement explicite** :
- `OperationPreview` (source/destination, opération, paramètres,digest exacts)
- `ConsentToken` lié session/request/generation, contenu/destination, deadline et usage unique, émis par CORE ; SERVICES n'édicte pas des permissions.
- `authorizeAtCommit / fence` vérifiant la décision de l'autorité CORE **au point précis avant effet**, la révocation, le compte d'opération et la version du snapshot. Utiliser barrières synchronisées pour les courses annulation/revocation/commit.
- Après engagement, la révocation ne peut pas promettre rollback général ; statut `COMMIT_STARTED / COMMITTED / UNKNOWN` documenté. Conservation reçus, journal durable, checksum et pas de replay implicite.
- Une approbation dans un document ou LLM ne donne jamais de capacité ; refus fail-closed si CORE indisponible.
Cette architecture peut être **proposée et testée en fixture** sans changer l'autorité réelle CORE. Si une décision interrepo/contrat sensible est requise, s'arrêter après la proposition.

**S2 export SAF** : dans un environnement de test Android isolé et explicitement opt-in, `ACTION_CREATE_DOCUMENT` ou équivalent contrôlé, **destination neuve** uniquement ; ne jamais écrire sur la source, ne jamais tronquer un document sélectionné existant. Écrire CSV/DOCX après consentement et relecture byte-level + digest sur vrai ContentResolver/DocumentProvider. Timeout, close bloqué, échec provider, ACK trompeur = UNKNOWN non réessayé et destination potentiellement partielle à afficher, aucune prétention rollback universel.

**S3 instrumentations** : SDK portables API24 et25 (puis26/35), vrais providers SAF de lab, TXT/PDF 2 pages, CSV `0.10+0.20=0.30`, DOCX relu, interruptions/crash/concurrence/révocation/duplications, journal saturé et permissions refusées. Distinguer tests JVM/Robolectric des tests appareil/émulateur. Compiler AAR+APK AndroidTest, analyses minSdk, absence réseau, licences.

**S4 raccord final17 outils** : comparer diff `SERVICES-FINAL-01` et `SERV-04A` ; conserver registre `ToolRegistry/Guard/ApprovalGate` unique, l'atelier autonome min26, les exports et leurs anciens tests. Adapter sous API24 seulement les capacités réellement prouvées ; `UNSUPPORTED` pour le reste. MEMORY de SERVICES n'est pas la mémoire confirmée CORE; BRIDGE reste Disabled.

**S5 document consommateur** : publier interface `ServicesAndroidToolPort` versionnée/fence, exemples et fixtures CORE, état de fermeture, idempotence/cancellation/ACK, matrices et hashes. Aucune connexion CORE en production dans ce dépôt.

### Critères d'acceptation
- Un DOCX/CSV neuf généré et relu sur vrai provider Android autorisé, source inchangée ; un refus sans consentement ne produit rien ; tests race avec point d'engagement documenté (révocation avant = aucun effet, après = résultat honnête).
- Suites historiques préservées, aucune baisse de garde/quota ou hausse minSdk CORE, pas de service exporté réseau.
- Si émulateur impossible ou sécurité fence non résolue : livrer S0/S1 fixture et signaler BLOCKED sans activer l'export.
- Prévoir un pilote limité CSV/TXT si DOCX reste bloqué plutôt que forcer une livraison prétendument complète.

### Chemins cibles
`services-portable-api/`, `services-android-adapter/`, `shared-runtime/`, `desktop/`, `android/` lecture de régression seulement, `docs/intensive-2026-10-22/REPORT-SERV-04A.md`, `reports/services-final-01/`, workflow et scripts.

## Consignes générales et rapport
Mission mère : https://github.com/bubth720/JARVIS-UNIFIED/issues/5. **Branche dédiée neuve**, base SHA et filiation Git explicites, pas de merge/auto-merge main, force-push ou écrasement des anciennes branches ; vérifier HEAD distants et AGENTS.md avant travail. Chaque dépôt garde la propriété de son code. Ne pas installer sur appareil personnel, toucher à une base réelle, transférer des données privées, importer du natif sensible ou changer signatures/permissions sans accord spécifique. Une mission bloquée livre un incrément autonome testé avec `PARTIAL/BLOCKED`, jamais un GO fictif.

Livrables `docs/fusion-02/REPORT.md`, `VALIDATION.json`, `PROVENANCE.json`, hashes des preuves et tableau contrat/code/JVM/Robolectric/instrumentation/physique/production. Renseigner module, lot, branche/base/HEAD, commits, fichiers, tests **exécutés**, CI à SHA exact, tests non exécutés, risques, limites, dernière action, prochain gate. Fournir PR **brouillon** si possible, sans merge.