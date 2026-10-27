# SERV-PF-01 — lot 1 : preuve SAF exacte, export production fermé

Date de consultation : 2026-10-27 UTC. Base mission vérifiée `365da117ccb29b59ed990711b2c3f72472ce2d9d`, parent `7c7fc95ad19e10de877b14fb5d3fe7f53b27d3e4`. Branche de développement `codex/prefusion-20261026-lot1`, PR brouillon vers `mission/prefusion-gates-20261026`. Le SHA de publication est le HEAD de la PR contenant ce rapport. Ce lot est documentaire, aucun runtime/test/permission/contrat modifié.

## Diagnostic, sans correction spéculative

| Run réellement relu | SHA exact | Conclusion |
|---|---|---|
| [40352356751](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40352356751) | 9dfbf60a908a7b44030ef060af4c164e349887be | FAILURE |
| [40404851239](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40404851239) | f8976cda0c68449d63a015c89ab1829a852b5e97 | FAILURE |
| [40410812418](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40410812418) | 7c7fc95ad19e10de877b14fb5d3fe7f53b27d3e4 | SUCCESS |
| [40412745341](https://github.com/bubth720/JARVIS-SERVICES/actions/runs/40412745341) | 365da117ccb29b59ed990711b2c3f72472ce2d9d | SUCCESS |

Les rapports SERV-08/09 décrivent respectivement les échecs API35 14/15 puis 13/15 (source.csv absente, retour au launcher), avant la reprise bornée du vrai picker. Le run SERV-09 puis celui de mission réussissent. Le vert du parent n'est pas attribué au HEAD : **une campagne distincte existe réellement au SHA mission**.

Lecture directe des jobs/logs du run 40412745341 : jobs API24 `121887318739`, API25 `121887318807`, API26 `121887318710`, API35 `121887318667`. Chacun termine par **Android XML: 15/15 passed; 0 failed; 0 skipped; 15 observed**. Ce sont 60 exécutions des mêmes 15 cas, pas 60 cas distincts. Les avertissements de démarrage émulateur et rendu restent dans les journaux GitHub ; aucun log brut ou détail d'infrastructure recopié ici.

Les jobs JVM `121887318786`, build/lint/API24 `121887318498` et instrumentation du diagnostic `121887318961` sont SUCCESS. Le nombre exact de tests JVM de cette campagne n'est pas déduit d'un ancien rapport/XML : le log consulté confirme BUILD SUCCESSFUL sans total exploitable. Le protocole CI exige toujours les 15 cas complets et conserve son contrôle XML strict.

## Commandes et limites

- `./gradlew :desktop:test --no-daemon` : exécuté par le job distant au SHA mission, SUCCESS. Tentative locale par `scripts/cloud-gradle.sh` échouée pendant téléchargement Gradle (proxy shell inaccessible), aucun test local compté.
- Build/lint et instrumentation API24/25/26/35 : observés dans la CI exacte ci-dessus, pas exécutés localement dans ce lot.
- Nouvelle CI du SHA contenant ce rapport : à observer indépendamment, aucun succès transféré automatiquement.
- Hash complet du nouveau document et source intacte : assertions conservées dans les 15 scénarios, code relu ; aucun nouvel artefact APK/XML retéléchargé ni recalcul de digest local dans ce lot.
- Appareil personnel, performance physique, intégration CORE et production : NOT_RUN.

Le code relu `FusionSafInstrumentedTest.java` utilise le vrai Activity Result et la navigation DocumentsUI, une seule reprise bornée, UID provider/worker distincts, readback complet et conservation de la source. Aucun URI/grant/résultat fabriqué. Aucun motif de changer ce code sur la seule base des deux anciens runs rouges ; sa stabilité à long terme reste à surveiller.

## Catalogue conservé : 17 historiques, 2 portés

Source inchangée : [CATALOGUE-MATRIX.json](../fusion-02/CATALOGUE-MATRIX.json), référence FINAL `d16dd84f27881460ec9f02d2a54d9ddff23187dc`, distincte du port API24. Les formats ci-dessous sont les schémas déclarés, pas une preuve de raccordement. Plafond déclaré par outil : 2 Mio mémoire ; `requires_network=false` ; version 1.0.0. Cette limite n'est pas un plafond de consommation native mesuré.

| Outil | Champs → sortie | Permission | État API24 |
|---|---|---|---|
| `actions.workflow.status` | digest, job → workflow_state_v1 | actions.run | UNSUPPORTED_NOT_RECONCILED |
| `analyst.table.inspect` | csv, schema → schema_inconsistencies_v1 | analyst.read | UNSUPPORTED_NOT_RECONCILED |
| `analyst.table.report` | csv, schema → sourced_calculation_v1 | analyst.read | UNSUPPORTED_NOT_RECONCILED |
| `bridge.offline.capabilities` | — → offline_protocol_capabilities_v1 | bridge.file | UNSUPPORTED_NOT_RECONCILED |
| `data.csv.normalize` | csv → csv_table | data.read | EXISTING_PORT |
| `data.table.transform` | csv, format, recipe, schema → table_preview_v1 | data.read | UNSUPPORTED_NOT_RECONCILED |
| `documents.docx.export` | path, text → file_digest | documents.export | UNSUPPORTED_NOT_RECONCILED |
| `documents.search` | query → document_hits | documents.read | EXISTING_PORT |
| `documents.template.render` | fields, template → derived_text_v1 | documents.read | UNSUPPORTED_NOT_RECONCILED |
| `documents.text.replace` | literal, replacement, text → derived_text_v1 | documents.read | UNSUPPORTED_NOT_RECONCILED |
| `guard.workspace.validate` | relative → safe_destination_v1 | documents.read | UNSUPPORTED_NOT_RECONCILED |
| `knowledge.selected.consult` | name, query, text → verified_passages_v1 | knowledge.read | UNSUPPORTED_NOT_RECONCILED |
| `memory.local.read` | query → local_memory_v1 | memory.read | UNSUPPORTED_NOT_RECONCILED |
| `memory.local.save` | expires, id, source, text, type → local_memory_receipt_v1 | memory.write | UNSUPPORTED_NOT_RECONCILED |
| `workspace.publish` | digest, job → private_file_v1 | documents.export | UNSUPPORTED_NOT_RECONCILED |
| `workspace.saf.export` | digest, job, target_ref → verified_fresh_document_v1 | documents.export | UNSUPPORTED_NOT_RECONCILED |
| `workspace.verify` | digest, job → verified_plan_v1 | documents.read | UNSUPPORTED_NOT_RECONCILED |

Les 15 outils UNSUPPORTED_NOT_RECONCILED ne sont pas importés dans le port. La recherche sélectionnée TXT/PDF et normalisation CSV seules sont raccordées ; pas un index NEXUS. Export CSV/DOCX du laboratoire uniquement ; l'export productif garde `SAF_EXPORT_UNQUALIFIED`.

## Contrat proposé, non approuvé

`CommitAuthorization` proposal/1 accepte generation>=0 ; `ServicesWirePreflight` refuse generation0 et >2^53−1 sur le wire proposé. Pas de +1 silencieux. COMMIT_STARTED n'est pas DISPATCHED ; COMPLETED n'est pas COMMITTED. Une confirmation UI ne remplace pas une autorité CORE : aperçu exact, session/request/generation/deadline, consentement unique, révocation sérialisée au point d'engagement et journal durable sont nécessaires. ACK perdu/après effet => UNKNOWN durable, aucun réessai implicite. Readback intégral exigé avant COMMITTED. La fence de laboratoire ne qualifie pas l'autorité de production ; ADR-002 attend décision.

Compatibilité : SDK API24 sans nouveau JNI, Java11 pour l'API portable et l'adaptateur ; diagnostic autonome API26 distinct ; JDK21/Gradle du wrapper pour build. Gson2.11.0/PDFBox Android2.0.27.0/BouncyCastle1.86 inchangés. Licences et redistribution d'un assemblage restent à examiner, aucune nouvelle dépendance ou permission.

**PASS** campagne de laboratoire au SHA365da117… ; **BLOCKED** autorité CORE/ADR-002 et export production ; **NOT_RUN** physique. La réussite émulateur n'ouvre aucun gate de production.

Rollback : revert du seul rapport, aucun état persistant modifié. Prochaine action : surveiller la nouvelle CI et préparer décision CORE/UNIFIED sur l'engagement durable, sans brancher la mutation. Mission complète non déclarée terminée.
