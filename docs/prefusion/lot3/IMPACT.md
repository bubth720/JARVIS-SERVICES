# SERV-PF-02 — surface d'impact

- Ajout : deux classes Java sous `desktop/src/test/.../prefusion` uniquement.
  Banc mémoire déterministe utilisant `CommitAuthorization.Preview` et
  `ServicesWirePreflight` existants, pas une copie de Guard/ApprovalGate en production.
- Ajout de `codex/prefusion-lot3-services` à la liste de branches CI existante.
  Aucun job/assertion retiré : suites JVM et4 campagnes de15 cas SAF restent exigées.
- Documents de proposition/rapport/provenance, sans modification des contrats gelés.

`services-portable-api/src/main`, `services-android-adapter/src/main`, historique
`android/`, permissions, signatures, ABI, dépendances et minSdk ne changent pas.
La JVM du banc simule autorité/horloge/journal/observations de close et relecture ;
ces objets ne sont pas empaquetés dans l'AAR/APK de production. Le banc ne lit
aucune URI, conversation ou base personnelle et ne crée pas de fichier utilisateur.

Le risque logiciel est limité au nouveau banc de test et à l'exécution de CI.
Une boucle de réconciliation/migration/runtime est volontairement absente.
