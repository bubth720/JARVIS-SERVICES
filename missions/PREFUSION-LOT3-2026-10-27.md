# SERV-PF-02 — Contrat de reçus : qualification isolée

Base : ea541d20b08f7ad0949354829a977f6752cbffa3
Dépôt : bubth720/JARVIS-SERVICES
Branche source : mission/prefusion-lot3-20261027

Codex : créer une branche indépendante depuis la branche source, vérifier les dernières CI et consulter docs/prefusion/ASSESSMENT.md. Préserver le comportement de production existant.

## Phase A — Invariants
Inspecter CommitAuthorization.java, ServicesWirePreflight.java et ServicesAndroidToolPort.java. Documenter les divergences : génération zéro côté SERVICES contre minimum un côté UNIFIED, distinction DISPATCHED/COMMIT_STARTED, COMMITTED/COMPLETED, consentement à usage unique et état UNKNOWN.

## Phase B — Simulateur hors production
Ajouter uniquement un banc de tests de contrats déterministes avec horloge injectable et données synthétiques. Vérifier annulation avant engagement, corruption du journal, commande doublonnée, perte ACK, fermeture et relecture incorrectes, absence de nouvelles tentatives après UNKNOWN. Ne pas brancher une autorité de production.

## Phase C — Décision ADR-002
Rédiger une proposition versionnée indiquant ordre des écritures, champs de corrélation, décision de révocation, risques, compatibilité et retour arrière. Garder ADR-002 NON APPROUVÉE tant qu'une décision humaine manque.

## Phase D — Tests et livraison
Relancer les suites JVM et les campagnes Android API24/25/26/35 (15 cas instrumentés chacune) au commit final. Toute régression doit être rapportée et corrigée sans supprimer de tests. L'export Android réel reste désactivé.

Livrables : code de test, rapport, commandes de tests exactes, SHA, blocages, rollback et PR brouillon vers cette branche. Aucun merge main, migration ou activation sensible.
