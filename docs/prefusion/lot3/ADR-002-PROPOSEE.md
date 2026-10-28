# ADR-002 — Autorité et engagement des mutations : proposition SERV-PF-02/1

**NON APPROUVÉE. Aucun changement du comportement de production.**
Mission : `missions/PREFUSION-LOT3-2026-10-27.md`.
L'orientation ADR-001 reste acquise ; cette proposition ne choisit ni IPC,
certificats/signatures, ni nouvel arbitre natif, ni migration réelle.

## Invariants et différences observées

- `CommitAuthorization.VERSION = jarvis.services.commit-proposal/1` est une
  proposition non raccordée à `ServicesToolPort/1`. `Preview` accepte génération
  >=0 ; `ServicesWirePreflight` refuse 0 et >9007199254740991 pour le wire proposé.
  **Pas d'ajout silencieux de1** : négocier explicitement une version/epoch neuve.
- `DISPATCHED` signifie demande transmise/attente, jamais début d'effet.
  `COMMIT_STARTED` signifie décision d'engagement, avant la première mutation.
  `COMPLETED` historique n'est pas `COMMITTED` : fin d'un callback et hash déclaré
  ne prouvent ni fermeture ni readback exact ni décision CORE durable.
- Le port Android reste à `SAF_EXPORT_UNQUALIFIED`; les outils lecture/CSV et
  leurs permissions restent identiques. Le journal et les consentements actuels
  ne sont ni migrés ni remis à zéro. La mémoire atelier n'est pas celle de CORE.

## Trois décisions ouvertes à transmettre à l'utilisateur

### D1 — Qui décide, et à quel instant ?

**Recommandation : CORE propriétaire unique**, décision `authorizeAtCommit`
linéarisée avec révocation et annulation sticky sur la même autorité live. Le
worker SERVICES reçoit une décision pour un aperçu exact, pas le droit de créer
une autorisation à partir d'un Context copié ou d'un document.

Avantages : une politique, pas de générations concurrentes ni de grants fabriqués.
Inconvénients : indisponibilité de CORE = refus/UNKNOWN ; les transports futurs
nécessitent revue d'identité, délais et authentification.
Alternative : délégation bornée d'un lease/capability à SERVICES. Elle peut améliorer
la disponibilité, mais ajoute des problèmes de révocation, clock, rejeu et crash ;
**non recommandée pour ce premier pilote** et non implémentée.

Le point exact proposé est la consommation unique de confirmation et l'inscription
durable de la décision CORE, sérialisées avec revoke, avant le callback d'effet.
Le verrou de décision ne doit pas rester tenu pendant les IO provider.

### D2 — Quel ordre des journaux, consentements et effets ?

Ordre candidat à examiner CORE/UNIFIED, aucune transaction distribuée promise :

1. CORE journalise PREPARED lié à epoch/session/request/generation/sequence,
   opération/version, hashes exacts source/contenu, destination neuve et deadline.
2. SERVICES journalise DISPATCHED/attente. Aucun effet ni confirmation consommée
   tant que l'autorité n'a pas décidé pour cet aperçu exact.
3. CORE décide et consomme la confirmation unique, journalise son engagement.
4. SERVICES reçoit la décision liée, journalise COMMIT_STARTED **avant** son effet.
5. SERVICES observe résultat/close/readback puis journalise COMMITTED ou UNKNOWN ;
   CORE ne présente un succès que depuis un reçu corrélé et durable reçu/validé.

Avantages : consommation unique et absence de reprise aveugle ; chaque fenêtre
se décrit. Inconvénients : plusieurs ACK et fenêtres de crash inter-journaux.
Une perte après3 peut consommer le consentement sans produire de fichier ; une
perte après4 peut avoir produit des octets. Ne promettre ni exactly-once universel
ni atomicité des deux bases. Aucune suppression/réinitialisation de journal.
Alternative : journal worker puis décision CORE avant consommation. Elle change
les fenêtres d'incertitude et nécessite un protocole de réconciliation distinct ;
ne pas l'adopter implicitement. Chaque propriétaire doit écrire son propre journal.

### D3 — Quelle preuve termine l'opération et permet la réconciliation ?

**Recommandation : fermeture observée + ACK + readback intégral des octets +
SHA attendu + original inchangé** avant COMMITTED. Le hash inclut le contenu
réellement relu, pas un petit échantillon ou le nom du fichier. Nouvelle destination
SAF choisie explicitement ; aucun `w` sur la source.

Avantages : contenu vérifiable, faux ACK/readback refusés. Inconvénients : coût
IO et disponibilité ; provider bloqué peut retarder la fin. Annuler une coroutine
n'atteste pas la fin d'un appel natif/ContentResolver.
Alternative « close seul suffit » : plus rapide, mais preuve insuffisante ; rejetée
comme recommandation. Réconciliation manuelle/lecture seule d'un UNKNOWN : possible
après preuves exactes corrélées, sans créer une nouvelle mutation ni réutiliser
le consentement. Son API est à proposer séparément.

Révocation avant engagement : aucun callback/effet. Après engagement : l'effet peut
aboutir avec avis, ou rester UNKNOWN ; ne pas promettre l'effacement des octets déjà
remis au provider. Timeout/kill/ACK perdu = UNKNOWN, journal conservé, pas de retry
implicite. Le changement de génération interdit les requêtes anciennes sans effacer
la décision historique d'une opération engagée.

## Compatibilité et risques

Les propositions UNIFIED v1/v2 ne sont pas modifiées. Pas de sérialisation de token
opaque, pas d'équivalence de chaînes d'état, UUID n'est pas preuve d'identité.
Une future adoption exige nouvelle version/revue des producteurs et consommateurs,
tests des champs manquants/versions non supportées, et essais de migration sur
copies synthétiques. Les schémas/ABI/minSdk/signatures historiques restent intacts.

Le banc JVM de ce lot est sous `desktop/src/test` uniquement, avec horloge et
journal mémoire synthétiques. Il teste transitions et échecs injectés, **pas la
durabilité SQLite, une panne physique, une identité UID réelle ni la fence CORE
connectée**. Les barrières concurrentes existantes sont conservées séparément.
Les campagnes SAF instrumentées réutilisent les15 cas existants ; un succès ne
branche toujours pas l'export de production.

## Rollback

Revert des fichiers du banc/docs et de l'ajout de déclenchement CI uniquement.
Aucun état utilisateur à restaurer. Ne pas purger les reçus UNKNOWN, ne pas
récupérer un token consommé, ne pas faire de downgrade d'une base réelle.
Les trois décisions D1/D2/D3 sont en attente d'approbation explicite et distincte
avant adaptation sensible des runtimes. Aucune présomption d'approbation par CI.
