# FUSION-02 — SERVICES / qualification Android et autorité d'engagement

Module `bubth720/JARVIS-SERVICES`. Mission propriétaire [#3](https://github.com/bubth720/JARVIS-SERVICES/issues/3), programme directeur [UNIFIED#5](https://github.com/bubth720/JARVIS-UNIFIED/issues/5), lus intégralement et archivés dans `evidence/`.
Branche **`feat/fusion-02-services`**, base **`f00091a639612b91eca1a301580c8f531249f1e3`**. Verdict : **PARTIAL** ; qualification de laboratoire, aucune activation d'export du port de production, aucun GO multi-module.

Les SHAs de code, d'artefacts et des preuves sont dans PROVENANCE.json et VALIDATION.json. Le runtime et les tests natifs correspondent au code1bbcdbc ; les changements ultérieurs concernent le garde d'identité/readiness Android35 en CI et le validateur documentaire, sans modification de ces binaires. Le commit contenant ces rapports est le commit de livraison documentaire ; aucune auto-référence SHA impossible n'est inventée.

## Résultat et responsabilités

- S0 : régressions port API24, empreinte Guava manquante corrigée, cache/lock Robolectric reproductibles en environnement cloud à home non inscriptible, CI historique diagnostiquée au niveau accessible. Jobs historiques conservés, matrice instrumentée du SDK24/25/26/35 ajoutée. Un résumé XML strict exige15 cas sans skip et publie les erreurs de fixtures synthétiques en annotations GitHub ; aucune suite n'est neutralisée.
- S1 : proposition additive compilable `api.proposal.CommitAuthorization`, version `jarvis.services.commit-proposal/1`, sans modification implicite des contrats UNIFIED v1 ni branchement de politique CORE. Six tests JVM de barrières, exactitude du consentement, expiration, annulation et non-réemploi après perte d'ACK.
- S2/S3 : export CSV/DOCX uniquement dans `src/androidTest`, vers un **nouveau** document du provider synthétique. Deux APK/UID distincts, vraie sélection DocumentsUI, grants persistants Android, ContentResolver, fermeture de flux, relecture complète des octets et SHA256, original inchangé. Confirmation exacte, journal privé durable, doublons refusés, UNKNOWN conservé, concurrence avant/après engagement, refus de permissions, révocation OS par le provider propriétaire, corruption et quota.
- Correction de sécurité réelle du SDK : verrou OS acquis **avant** ouverture SQLite ; gestionnaire de corruption explicite sans suppression/réinitialisation des reçus. Les échecs d'ouverture libèrent le verrou. Aucun schéma, base historique, Guard ou permission CORE modifié.
- S4 : comparaison explicite des17 outils de l'atelier final et du port. Deux outils sont déjà raccordés (`documents.search`, `data.csv.normalize`) ; les15autres sont `UNSUPPORTED_NOT_RECONCILED` dans la matrice. Les codecs peuvent être réutilisables sans que cela prouve leur raccord/qualification dans ce catalogue. Aucun copier-coller de moteur NEXUS ou second registre.
- S5 : contrat consommateur, matrice, runbook, provenance et preuves livrés. Le port runtime continue de refuser l'export par `SAF_EXPORT_UNQUALIFIED`, y compris après confirmation de laboratoire.

L'autorité CORE décide et confirme. SERVICES ne reçoit aucun droit d'un texte ou d'un modèle. NEXUS reste responsable du stockage des connaissances confirmées ; MEMORY/BRIDGE ne sont pas activés dans ce port. L'atelier autonome à17 outils reste intact sur sa branche min26.

## Filiation et dépendances vérifiées

Les têtes distantes des huit dépôts ont été relues sans modification : `evidence/remote-heads.json`. Un second relevé final est conservé dans `remote-heads-final.json` : de nouvelles branches FUSION-02 sont apparues entre-temps. Leurs rapports et contrats UNIFIED/CORE ont été relus intégralement ; voir UPSTREAM-HANDOFF.md. NEXUS a maintenant9 tests instrumentés par API24/25 avec FTS5 indisponible ; CORE reste C0 désactivé et UNIFIED a fiabilisé sa CI logicielle. Ces faits corrigent les constats historiques, sans lever le gate RAG ou approuver notre fence. Les branches SERVICES API24 et FINAL ont pour merge-base `3decdc47d82f9b43713c48cab244762941770a81` et divergent ; aucun merge/cherry-pick aveugle.

| Référence | SHA vérifié | Consommation |
| --- | --- | --- |
| SERVICES port API24 | f00091a639612b91eca1a301580c8f531249f1e3 | Base de cette branche |
| SERVICES FINAL17 | d16dd84f27881460ec9f02d2a54d9ddff23187dc | Référence séparée, 129 tests relancés dans une archive immuable sans autre checkout modifié |
| CORE FUSION-01 | 36761cea101b5f88b87cd4136dc075d28a1adb96 | Audit de référence, aucun code CORE modifié/embarqué |
| NEXUS API24 | 374ea2e2fd640e78f2f5e12e60e75b493420e504 | Gate producteur externe, aucun moteur NEXUS inclus |
| UNIFIED FUSION-01 | 3caa57d91ce0dba31043e7cb6373a5e2df8b9fa4 | Contrat de gouvernance, pas de mutation v1 |

Dépendances runtime inchangées : Gson2.11.0, PDFBox Android2.0.27.0, BouncyCastle1.86. Aucune bibliothèque SQLite/native ajoutée. Les manifests SDK restent min24 sans permission ; le diagnostic historique garde min26. AAR sans JNI et sans classes d'export/contrôle laboratoire. Le SDK compilable n'embarque ni JDBC ni le runtime desktop. Le `Guard.Context` du port existant reste un instantané : la nouvelle fence n'est pas branchée sur cette autorité runtime et aucune correction de révocation CORE en production n'est revendiquée.

Le nouveau POM parent Guava33.3.1-jre était **absent** du verrou, pas une empreinte contradictoire : téléchargé par HTTPS depuis Maven Central, comparé au SHA1 publié, puis ajouté SHA256 `55441db27e8869dfefe053059bdf478bdc7e95585642bf391f0023345fd56287`. Aucun checksum existant remplacé, aucune vérification TLS/Gradle désactivée. Aucune nouvelle dépendance runtime ni modification de licence de distribution ; PDFBox/Gson Apache2, BC licence permissive déjà présente. APK debug/testOnly non destiné à distribution.

## Tests réellement exécutés

| Campagne | Résultat | Nature/preuve |
| --- | --- | --- |
| Branche courante JVM | **100/100**, 0 échec/ignoré | 94historiques +6nouveaux ; XML et `evidence/jvm-final.log` |
| Branche courante Robolectric | **40/40**, 0 échec/ignoré | SDK24/25/26/35 **simulés**, pas instrumentation ; XML |
| Référence FINAL17 immuable | **129/129**, 0 échec/ignoré | Exécutés à nouveau sur d16dd84, séparément ; pas intégrés au port |
| Compilation API24 directe | **PASS**,23sources | Vrai android.jar24 et stubs lambda officiels, aucun bootclasspath JDK cachant API absente |
| AAR/APK du SDK et fixture | **PASS** | Build réel et empreintes dans artefacts |
| Diagnostic historique | **PASS build/Lint** | Aucun fichier sous android/ modifié ; sa campagne native reste distincte |
| Lint SDK et diagnostic | **0erreur/1 warning chacun** | TrustAllX509TrustManager dans BC, aucun abaissement de sévérité |
| Lint APK fixture | **0erreur/2 warnings** | DataExtractionRules, MissingApplicationIcon, APK testOnly/allowBackup=false |
| Instrumentation API24 | **15/15**,0 échec/ignoré | Vrai Android, logs terminaux et APK figés |
| Instrumentation API25 | **15/15**,0 échec/ignoré | Vrai Android, logs terminaux et APK figés |
| Instrumentation API26 | **15/15**,0 échec/ignoré | Vrai Android, logs terminaux et APK figés |
| Instrumentation API35 en CI | **15/15**, gate XML obligatoire, job vert | Vrai émulateur x86_64/KVM du run10cd ; artefact publié, XML distant non téléchargé ici |
| Instrumentation API35 locale | **NOT_RUN / BOOT_BLOCKED** | Zéro test installé/exécuté, limite de démarrage TCG sans KVM ; logs OS distincts |
| Appareil physique/production | **NOT_RUN** | Aucune installation utilisateur autorisée/exécutée |

Les15mêmes cas ont été exécutés sur chacun des trois SDK, soit45 exécutions natives locales. Les fixtures sont TXT/PDF réel de2 pages, CSV réel (`0.10 + 0.20 = 0.30`) et DOCX généré/relu. Les métadonnées instrumentées assertent des UID distincts pour provider/worker. Les hashes APK lient chaque campagne à ses vrais binaires. Le runner n'accepte que localhost:5581, qemu, API exacte et boot achevé ; il ne découvre aucun appareil.

## Scénarios du programme directeur

| Scénario | Portée effectivement vérifiée / limite |
| --- | --- |
| A hors ligne | Manifests sans INTERNET, aucun transport activé ; état effectif des radios non qualifié |
| B TXT → FTS5 → CORE | **BLOCKED externe**, pas de moteur NEXUS ni de composition CORE dans ce dépôt ; recherche littérale SERVICES seule |
| C PDF → citations | PDF réel2 pages, trois hits TXT/PDF, SHA/page/offsets vérifiés dans le port |
| D CSV | Vrai outil existant, normalisation/dédoublonnage, somme exacte0.30, nouveau fichier relu, source inchangée |
| E/F DOCX | Refus sans consentement et runtime désactivé ; export confirmé et relu uniquement en laboratoire |
| G révocation/ACK | Barrières avant/après, vraie révocation OS, ACK trompeur/perdu, duplicate/refus/reçu UNKNOWN ; pas de rollback universel |
| H arrêt/reprise | Réouverture SQLite et reçu abandonné injecté ; **pas** de force-stop au milieu d'un IO |
| I plateformes/lifecycle | Campagnes natives détaillées dans VALIDATION ; rotation/pression mémoire réelle/appareil physique non exécutés |
| J dépendances absentes | Les outils et fixtures se compilent/exécutent sans CORE/NEXUS/LLM/VOICE/Avatar ; aucune réponse IA simulée |

## Point d'engagement et incertitude

Voir COMMIT-CONTRACT.md. Sous le même verrou détenu par l'autorité hôte : valider tous les champs de l'aperçu, génération, deadline monotone, annulation sticky, quota et consentement opaque à usage unique ; consommer le consentement puis appeler l'effet une fois hors du verrou. Cette décision est le point d'engagement.

Révocation avant : aucun effet. Révocation après : l'opération engagée peut aboutir, même si le premier octet est écrit ensuite ; aucune promesse d'effacement. La notification/persistance de cette révocation dans CORE doit être arbitrée avant intégration. Le journal SERVICES précède la mutation ; après close et relecture exacte seulement, COMMITTED. Un ACK trompeur/perdu ou un reçu inachevé devient UNKNOWN et ne donne jamais un droit de retry.

L'exporteur de test ne prétend pas borner de force un IO provider bloqué. Un timeout ou une annulation après engagement ne prouve pas l'arrêt du worker. Crash/reprise est testé par insertion d'un reçu COMMIT_STARTED abandonné et réouverture SQLite ; **pas par force-stop pendant une écriture native**, ni coupure physique. Rotation, close durablement bloqué, provider utilisateur arbitraire et alias d'URI restent à qualifier. Aucun rollback universel promis.

## Échecs initiaux conservés et corrections

1. `baseline.log` : refus de vérification du POM Guava parent manquant ; correction vérifiée ci-dessus.
2. `baseline-retry.log` :40 tests Robolectric échouent sur le verrou `/home/agent` non inscriptible ; répertoire Java user.home de ce runner sous build/, sans réaffecter HOME système. La suite40 repasse.
3. Premiers pilotes SAF : sélection accessible grilles/listes mais interaction fichier invalide ; variantes de diagnostic conservées, pas comptées comme PASS. Un événement clavier à temps0 était rejeté comme périmé. Hypothèse initiale de dérive après interruption investiguée par cold boot, **insuffisante** ; correction des événements horodatés.
4. Premier retour du picker : même UID provider/consommateur => aucun grant persistable inter-apps ; refus SecurityException conservé. Isolation en second APK testOnly, sans affaiblir MANAGE_DOCUMENTS.
5. Contrôle initial des fautes via l'URI protégée refusé : remplacé par un receiver de laboratoire à permission signature, aucune permission de production ajoutée.
6. Premier vrai lot API24 :14PASS/1FAIL, libération du grant persistant laissant le droit transitoire ; correction du scénario avec vraie révocation par le provider propriétaire et vérification OS, sans changer le contrôle de permissions.
7. Premier lot API25 :14PASS/1FAIL, ACK du contrôle de fixture absent au démarrage dans sa fenêtre10 s. Les logs système montrent des timeouts TIME_TICK et de lancement pendant le cold boot TCG. La commande de contrôle est désormais un broadcast explicite foreground, avec attente bornée30 s ; signature, ACK exact,15assertions/scénarios et deadlines des opérations métier restent exigés. Ce délai de démarrage du dispositif de fautes n'est pas un budget de traitement documentaire.

8. API26 premier lot :4PASS/11FAIL, sélecteur masqué par « System UI isn't responding ». Capture conservée, appui Wait sur cet émulateur synthétique puis même campagne à15PASS, sans changement d'assertion. Ce succès ne valide pas un cold boot universel. La CI vérifie désormais l'état OS avant les tests, conserve des captures et permet au plus un Wait **System UI** ; un autre ANR ou un ANR persistant bloque le job. Ce dispositif ne désactive pas les ANR et ne masque jamais un échec d'application.
9. CI863f822 : échecs de sélection sur26/35, et au moins un source.txt hors écran. Le pilote parcourt maintenant la liste SAF virtualisée au lieu de considérer un fichier invisible comme absent ; aucun grant ni résultat n'est fabriqué. Captures après test et code retour Gradle sont conservés.

Aucun test historique supprimé, aucune assertion remplacée par un succès artificiel. Les essais interrompus ont `complete=false` ; un pilote isolé à1test n'est pas la campagne15. Le changement d'APK en cours d'un build est évité par snapshots locaux des deux APK lors du lancement du runner final.

## CI distante et PR

SERV-04A run40115305244 : JVM et instrumentation **du diagnostic historique** réussis, android-build échoué à l'étape `SERV-04A Android24 adapter regressions and library`. Les logs détaillés exigent une connexion ; cause distante fine **UNKNOWN**, distincte du défaut de verrou reproduit localement.

Première CI FUSION-02 au code51da6fa : [run40287588184](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40287588184), FAILURE,8m56. Les jobs historiques JVM/build/Android26 sont passés ; nouveaux jobs de ports ont échoué (pilote/installation), dont API35 adb exit224. Ce résultat est conservé dans `evidence/ci-first-fusion02.json`, pas qualifié de succès.

CI au code5bc53de : FAILURE,12m09, ports26/35 en échec. CI863f822 : FAILURE,15m10, annotations de vrais échecs maintenant accessibles publiquement (source.txt invisible26, racines du picker35). CI1bbcdbc : étape de readiness35 en échec avant qualification (raison détaillée non accessible dans les logs authentifiés). La revue du garde d'identité a relevé sa propriété legacy ; la source AOSP Build.java utilise `ro.boot.qemu` (preuve et URL archivées) ; le garde choisit désormais cette propriété pour35, en conservant qemu/API exacte/boot/serialCI. Les diagnostics de readiness sont publiés en annotations.

La CI finale est **SUCCESS au commit10cdce58fe3cf451c7a1b4220081cece1b82a948**, **7 jobs réussis en 8 min 20 s** : [run40348805777](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40348805777). Les quatre jobs de ports24/25/26/35 passent la même validation XML obligatoire 15 cas,0 échec/ignoré. Les identifiants/statuts des sept jobs et les digests d'artefacts annoncés par GitHub sont conservés dans ci-final-fusion02.json. Les XML/captures CI sont publiés en artefacts ; leur archive n'a pas été téléchargée/recalculée ici (API authentifiée indisponible). Le commit documentaire final ne reçoit pas automatiquement la CI du code10cd.

Correction finale : KVM fourni par le runner Ubuntu rendu accessible au seul UID éphémère pour API35, puis `emulator -accel-check` obligatoire. Méthode conforme à la recommandation de l'action, sans règle world-write ni modification du sandbox Codex, des permissions produit ou d'un appareil personnel. Pas de chargement natif/SQLite dans l'application. Les autres jobs préexistants restent inchangés.

État exact et SHA dans VALIDATION.json. L'existence du workflow ne prouve aucun PASS. CORE run40215604539 est SUCCESS ; UNIFIED40214722427 échoue **avant exécution**, syntaxe YAML ligne48 ; NEXUS40115049468 échoue, logs détaillés non accessibles. Autres dépôts laissés intacts.

La création de PR brouillon via `gh pr create --draft` est refusée par l'accès API GraphQL (`Forbidden`). Branche/commits accessibles par le proxy Git. PR-DRAFT.md contient le texte prêt à l'emploi, base conseillée `feat/serv-04a-android-tool-port`. Aucune PR non brouillon ni fusion tentée.

## Limites, risques et prochains gates

- **REVIEW_REQUIRED CORE/UNIFIED** pour authorizeAtCommit/génération/erreurs/reçus/avis après engagement. Le draft UNIFIED2 exige des générations1..2^53−1 ; notre proposition locale accepte0 et ses reçus ne sont pas le wire draft. Aucun adaptateur implicite : écarts détaillés dans UPSTREAM-HANDOFF.md. Cette proposition ne remplace pas l'autorité réelle et n'active aucun export runtime.
- Gate NEXUS FTS5/API24 et composition CORE : dépendances externes, pas de ANDROID_MULTI_MODULE_GO déclaré. Les succès d'outils SERVICES ne prouvent pas une application CORE composée.
- Les15 outils finaux non raccordés demandent une réconciliation des signatures/Path/quotas/minSdk et une compilation consommateurs ; ni fusion du catalogue ni abaissement forcé de minSdk de l'atelier.
- Le contrôle de mode avion en shell demande le setting1 mais le broadcast protégé peut être refusé. Sa sortie est conservée : **pas de preuve complète de radio arrêtée**. Les manifests sans INTERNET et l'absence de transport restent vérifiés ; pas de téléchargement produit.
- API35 local : image officielle35r2 vérifiée, aucun KVM sur ce Cloud. Démarrage resté incomplet/ANR System UI au délai ;0test produit et aucune installation de ses APK. Les avertissements TCG AVX/F16C sont conservés comme contraintes observées, pas présentés comme cause unique prouvée. RAM demandée1536MiB, portée automatiquement à2560MiB par l'émulateur officiel. L'émulateur a été arrêté ; le succès API35 est **celui de CI avec KVM**, distinct de cette tentative locale. Seules des copies compressées de nos outils déjà installés et une ancienne extraction inutilisée ont été retirées pour l'espace, avec hashes ; aucun fichier de dépôt/utilisateur supprimé.
- Pas de mesure physique batterie/thermique, performance ARM64 ou PSS de pic. Les durées du runner incluent installation/IO hôte et ne sont pas des benchmarks produit. Les limites2 Mio/100pages/250kcaractères ne prouvent pas un plafond de RAM PDFBox.
- Données/DB personnelles, migrations, signatures de production, réseau/IPC/BRIDGE, JNI/SQLite externe, main et autres dépôts : inchangés. Les bases du laboratoire sont exclusivement synthétiques.
- Avant activation : revue inter-dépôts, qualification provider externe et lifecycle/kill, intégration CORE derrière flagOFF, puis autorisation distincte si étape sensible ou appareil personnel.

## Commandes reproductibles

Voir RUNBOOK.md. Le validateur a également rejeté quatre contrôles négatifs (faux GO, sortie modifiée, statut natif forgé, XML sans cas). Ce sont des vérifications du validateur, pas des tests applicatifs supplémentaires. Les empreintes vérifient la cohérence des octets, pas une attestation indépendante de vérité physique. Journaux, XML, snapshots de missions, catalogue et manifest/hashes dans `evidence/`. Les métadonnées hostname/proxy des runners sont expurgées, sans modifier assertions, résultats, versions ou empreintes d’artefacts ; originaux conservés localement et hashes avant/après dans publication-normalization.json. Les vrais fichiers Android récupérés après test sont dans `examples/` ; leur copie debug hôte est explicitement distinguée du chemin SAF testé. Vérifier `python3 scripts/check-fusion02-evidence.py` avant consommation. Aucune réussite native/CI/physique n'est héritée d'une simulation ou d'un autre SHA.
