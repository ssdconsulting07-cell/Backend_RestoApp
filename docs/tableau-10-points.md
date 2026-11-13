# Les 10 points de vigilance, fonctionnalité par fonctionnalité

Backend SenYummies, état de `develop` au 13 novembre 2026 (lots 1, 2, 3, paiement simulé et 5).
Chaque ligne dit quels points s'appliquent et **comment c'est géré dans le code**, avec le test qui le prouve
quand il existe.

Rappel des points : **1** concurrence · **2** idempotence · **3** atomicité · **4** montée en charge ·
**5** contrôle d'accès · **6** cohérence entre services · **7** déploiement sans coupure ·
**8** conflits / hors ligne · **9** IA · **10** systèmes distribués.

## Vue d'ensemble

| Fonctionnalité | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Authentification staff | | | | ● | ● | | | | | |
| Migrations et données de démo | | | ● | | | | ● | | | |
| Catalogue, annonces, zones | ● | | ● | ● | ● | | | ● | | ● |
| Photos | | | | ● | ● | | | | | ● |
| Création de commande (APP) | ● | ● | ● | ● | ● | ● | | ● | | |
| Vente comptoir | | ● | ● | | ● | | | | | |
| Suivi client | | | | ● | ● | | | | | |
| Changement de statut | ● | | ● | | ● | ● | | ● | | |
| Temps réel (SSE) | | | ● | ● | ● | ● | | | | ● |
| Paiement (init, webhook, expiration) | ● | ● | ● | | ● | ● | | | | |
| Journal d'audit | | | ● | | ● | | | | | |
| Statistiques | | | | ● | ● | | | | | |
| Exploitation (Docker, profils) | | | | ● | ● | | ● | | | ● |

Le point 9 ne s'applique à aucune fonctionnalité : voir plus bas.

## Détail

### Authentification staff (`/auth/login`, comptes, JWT)

| Point | Comment c'est géré |
|---|---|
| 4 | `POST /auth/login` limité à 10 essais par minute et par IP réelle (429 `TROP_DE_REQUETES` + `Retry-After`). JWT sans état : n'importe quelle instance valide un jeton. *Test : `LimitationDebitIT`.* |
| 5 | Mots de passe BCrypt. Rôles portés par le jeton, vérifiés sur chaque route (`SecurityConfig` + sécurité par méthode). Un seul compte MANAGER, créé au démarrage, jamais par l'API (bug corrigé : la condition bloquait toute création de compte). En profil prod, le backend **refuse de démarrer** si le secret JWT est celui par défaut ou fait moins de 32 octets, si le seed est actif, si CORS contient `*`. *Tests : `AuthApiIT`, `VerificationConfigurationProdTest`.* |

### Migrations Flyway et données de démo

| Point | Comment c'est géré |
|---|---|
| 3 | Chaque migration MySQL est un fichier ; une migration qui échoue s'arrête et Flyway refuse de redémarrer tant qu'elle n'est pas réparée (pas d'état « à moitié migré » ignoré). |
| 7 | `V1` = schéma réel existant, `baseline-on-migrate` pour les bases déjà en service (testé sur une copie de la vraie base, lots 1 à 3). Une migration par lot (V1.1 à V5), **uniquement des ajouts** : aucune colonne supprimée ni renommée. `ddl-auto=validate` : Hibernate vérifie le schéma au démarrage, il ne le modifie jamais. Données de démo dans un emplacement séparé (`db/demo`, scripts répétables `R__`), chargé **seulement** en profil dev ; le profil prod refuse cet emplacement. Healthcheck `GET /api/v1/health` utilisé par Docker et par le compose de démo. *Tests : `MigrationsFlywayIT`, `RepriseBaseExistanteIT`, `DonneesDemoIT`.* |

### Catalogue, annonces, zones (lecture publique, écriture Gérant)

| Point | Comment c'est géré |
|---|---|
| 1 | Produit passé en rupture pendant qu'un client commande : la commande relit chaque produit **dans sa transaction** ; s'il n'est plus publié, 400 `PRODUIT_INDISPONIBLE` (ou `PLAT_INDISPONIBLE`, `ZONE_INDISPONIBLE`) avec le champ fautif. Pas de stock chiffré dans le projet : rien d'autre à réserver. Une commande validée une milliseconde avant la dépublication est acceptée (le produit était disponible au moment de la lecture). |
| 3 | Archiver une catégorie non vide avec `reassignerVers` : produits déplacés et catégorie archivée dans la même transaction. |
| 4 | Lectures publiques en cache serveur (vidé à chaque écriture du Gérant) et `Cache-Control: public, max-age=30`. Lot de 50 pour les chargements liés (`default_batch_fetch_size`), pas de requête N+1. |
| 5 | Lecture publique limitée à `ACTIF` (`?statut=` autre que ACTIF sans droits Gérant : 403) ; produit visible seulement si sa catégorie l'est aussi. Écriture réservée au GERANT. Un secteur officiel est masqué, jamais supprimé. URL d'image : `https://` ou photo déposée uniquement (pas de `javascript:` ni d'adresse interne). |
| 8 | Deux Gérants modifient la même fiche : chaque ressource porte une `version` ; la deuxième modification reçoit **409 `VERSION_CONFLICT`** au lieu d'écraser la première. *Tests : `CatalogueApiIT`, `AnnoncesZonesApiIT`.* |
| 10 | Le cache est en mémoire de l'instance : avec plusieurs instances, voir le point 10 plus bas. |

### Photos (`POST /photos`, `GET /photos/{nom}`)

| Point | Comment c'est géré |
|---|---|
| 4 | Taille refusée avant lecture (2 Mo), dimensions refusées avant décodage (6 000 px, 36 Mpx) pour éviter qu'une image piégée sature la mémoire. Redimensionnement à 1 600 px. Lecture publique avec cache immuable (nom unique). |
| 5 | Dépôt réservé au GERANT. Vrai type vérifié par signature (pas l'extension ni l'en-tête), ré-encodage JPEG/PNG qui supprime les métadonnées EXIF (position GPS de la photo). Nom généré par le serveur (UUID), jamais fourni par le client : pas de traversée de répertoire. Conteneur non-root. *Tests : `PhotosApiIT`, `TraitementPhotoTest`.* |
| 10 | Stockage disque local derrière l'interface `PhotoStorage` : une seule instance. Voir « Reste à faire » (stockage objet). |

### Création de commande APP (`POST /commandes`)

| Point | Comment c'est géré |
|---|---|
| 1 | Produits, prix et zone relus dans la transaction (voir catalogue). |
| 2 | En-tête `Idempotency-Key` : même clé + même corps = première réponse rejouée (`Idempotency-Replayed: true`), aucune deuxième commande ; même clé + corps différent = 409. La clé est réservée dans une transaction séparée pour que deux envois simultanés ne passent pas tous les deux. **L'App Client ne l'envoie pas encore** (voir « Reste à faire », lot 4). *Test : `CommandesApiIT`.* |
| 3 | Commande + lignes + historique + paiement + événement temps réel + audit + mémorisation de la clé d'idempotence dans **une seule transaction**. Une erreur à n'importe quelle étape annule tout. |
| 4 | 10 commandes par minute et par IP réelle (429). IP lue dans `X-Forwarded-For` posé par le nginx des fronts, en ne faisant confiance qu'aux proxys du réseau privé. |
| 5 | Canal APP public ; canal COMPTOIR réservé au VENDEUR. Statut et statut de paiement envoyés par le client **ignorés** ; prix, sous-total, frais et total **recalculés** par le serveur (400 `PRIX_INCOHERENT` avec le champ si le client annonce autre chose). Nom et prix figés sur chaque ligne. Aucune donnée personnelle (nom, téléphone, position) dans les logs. |
| 6 | Commande créée `CREEE` / `en_attente`, puis payée **sur confirmation du serveur** (voir paiement). |
| 8 | Réseau coupé pendant l'envoi : le renvoi avec la même clé ne crée pas de doublon (point 2). |

### Vente comptoir (VENDEUR)

| Point | Comment c'est géré |
|---|---|
| 2 | Même `Idempotency-Key` que les commandes APP (double clic, réseau coupé). |
| 3 | Vente, encaissement, monnaie rendue et audit dans la même transaction. |
| 5 | Réservée au VENDEUR ; montant reçu contrôlé (400 `MONTANT_INSUFFISANT`), monnaie rendue calculée par le serveur. Le vendeur ne voit que ses propres ventes dans ses statistiques. |

### Suivi client (`GET /commandes/{id}`, `GET /commandes?clientTelephone=`)

| Point | Comment c'est géré |
|---|---|
| 4 | 30 requêtes par minute et par IP sans jeton (429). |
| 5 | Les numéros de ticket sont séquentiels (énumérables) : le détail d'une commande exige un **jeton de suivi secret** (`X-Jeton-Suivi`), renvoyé une seule fois à la création et stocké haché (SHA-256) ; sans lui, **404** (pas 403, pour ne pas confirmer que la commande existe). La recherche par téléphone renvoie une **vue réduite** (pas d'adresse, pas de position, pas de nom). Un client ne voit jamais la commande d'un autre. *Test : `CommandesApiIT`.* |

### Changement de statut (`PATCH /commandes/{id}/statut`)

| Point | Comment c'est géré |
|---|---|
| 1 | Cuisine et Livreur passent la même commande de PRETE à EN_LIVRAISON au même instant : **verrou exclusif** sur la ligne (`SELECT … FOR UPDATE`). Le premier applique la transition ; le second attend, relit la commande déjà EN_LIVRAISON et reçoit **409 `STATUT_DEJA_APPLIQUE`**. Jamais deux transitions appliquées. *Test : `CommandesApiIT` (appels simultanés).* |
| 3 | Transition + historique horodaté + événement temps réel + audit dans la même transaction. |
| 5 | Table des transitions par rôle et par mode, identique à `transitions.js` du Manager : sinon **403**. Chaque rôle ne voit que sa file (Cuisine : commandes payées ; Livreur : livraisons ; Vendeur : comptoir). |
| 6 | Commande payée puis annulée : drapeau de remboursement à traiter (compensation signalée, pas de remboursement automatique tant qu'il n'y a pas de prestataire). |
| 8 | `version` facultative dans le corps : si elle ne correspond plus, 409 `VERSION_CONFLICT` (l'écran affichait une commande périmée). |

### Temps réel (`GET /temps-reel/commandes`, SSE)

| Point | Comment c'est géré |
|---|---|
| 3 | **Outbox** : l'événement est écrit en base dans la transaction de l'action. Une commande annulée par erreur ne produit donc jamais d'événement, et un événement n'est jamais perdu si le serveur s'arrête juste après la validation. |
| 4 | Un relais lit l'outbox chaque seconde (`FOR UPDATE SKIP LOCKED`) et pousse aux écrans connectés ; battement de cœur toutes les 25 s ; reconnexion automatique du navigateur (5 s) avec `Last-Event-ID` pour rattraper ce qui a été manqué. |
| 5 | **Staff authentifié uniquement** ; chaque événement est filtré par rôle (le Livreur ne reçoit que les livraisons, etc.). |
| 6 | Paiement → commande PAYEE → événement → écran Cuisine, dans cet ordre, sans perte. |
| 10 | Les connexions SSE sont en mémoire de l'instance : voir le point 10. |

SSE plutôt que WebSocket STOMP : flux à sens unique (serveur → écrans), passe par le nginx des fronts avec
un simple bloc sans tampon (déjà ajouté, PR Client #23 et Manager #40), reconnexion native.

### Paiement (`/paiements/init`, `/paiements/webhook`, expiration)

| Point | Comment c'est géré |
|---|---|
| 1 | Webhook et expiration qui arrivent en même temps sur la même commande : chaque opération **verrouille la commande** ; le second voit le résultat du premier. Paiement reçu après l'annulation : commande laissée ANNULEE, remboursement signalé. |
| 2 | Webhook reçu plusieurs fois : identifiant d'événement enregistré (table d'idempotence des notifications), deuxième réception = `DEJA_TRAITEE`, aucun double effet. *Test : `PaiementsApiIT`.* |
| 3 | Paiement + statut de commande + événement + audit dans une transaction. |
| 5 | Webhook **signé** (HMAC-SHA256 + horodatage contre le rejeu) : 401 `SIGNATURE_INVALIDE` sinon. Montant contrôlé (`MONTANT_INCOHERENT`). Relance du paiement avec le jeton de suivi uniquement. **Aucune donnée de carte ne transite chez nous.** Le paiement simulé est **impossible en profil prod** (le backend refuse de démarrer). |
| 6 | Saga : paiement confirmé → PAYEE → Cuisine ; paiement échoué ou non reçu sous 30 min → ANNULEE / `echoue` ; payé puis annulé → remboursement signalé. |

### Journal d'audit (`GET /audit`, `/audit/export.csv`)

| Point | Comment c'est géré |
|---|---|
| 3 | Écrit **dans la transaction de l'action** : une action refusée ou annulée ne laisse aucune trace, une action validée en laisse toujours une. |
| 5 | Réservé au MANAGER. Export CSV protégé contre l'injection de formules (`=`, `+`, `-`, `@` neutralisés pour Excel). Pas de données personnelles de clients dans les libellés. *Tests : `AuditStatistiquesIT`, `ExportCsvTest`.* |

### Statistiques

| Point | Comment c'est géré |
|---|---|
| 4 | Calculées en SQL agrégé (pas de chargement des commandes en mémoire), index `commandes(statut, cree_le)`. |
| 5 | MANAGER et GERANT ; le VENDEUR ne voit que ses propres ventes comptoir. Chiffre d'affaires = commandes payées et non annulées. |

### Exploitation (Docker, profils, configuration)

| Point | Comment c'est géré |
|---|---|
| 4 | Application **sans état** (JWT, idempotence et outbox en base) : prête à être dupliquée, sous les réserves du point 10. |
| 5 | Toute la configuration par variables d'environnement, aucun secret en dur ; vérifications au démarrage en profil prod ; Swagger et messages d'erreur détaillés désactivés en prod ; conteneur non-root. |
| 7 | Healthcheck dans l'image ; le compose de démo attend que MySQL puis le backend soient sains avant de lancer les fronts. Arrêt propre des flux SSE à l'arrêt du serveur. |
| 10 | Une seule base MySQL principale. |

## Point 9 : IA

**Il n'y a pas d'IA dans ce projet.** Aucun modèle, aucune génération ni décision automatique : tous les
calculs (prix, totaux, frais, statistiques) sont déterministes et testés. Si une fonctionnalité d'IA est
ajoutée plus tard (suggestions de menu, réponses automatiques…), elle devra citer ses sources, marquer ce
qui est incertain et refuser de répondre sans preuve.

## Point 10 : cohérence plutôt que disponibilité

**Choix actuel : cohérence.** Une seule base MySQL principale ; si elle est injoignable, le backend refuse
les écritures (erreur) plutôt que d'accepter des commandes qu'il ne pourrait pas garantir. Le healthcheck
passe au rouge et Docker le signale.

Aujourd'hui, **une seule instance du backend** est prévue. Ce qui est en mémoire de l'instance et ce qu'il
faudra changer pour en lancer plusieurs :

| Élément en mémoire | Problème à plusieurs instances | À faire |
|---|---|---|
| Connexions SSE + relais de l'outbox | Le relais d'une instance prend l'événement (`SKIP LOCKED`) : seuls les écrans connectés à **cette** instance le reçoivent. | Chaque instance lit l'outbox avec son propre curseur (diffusion à tous), ou passage par un bus (Redis Pub/Sub, RabbitMQ). |
| Limitation de débit | Compteurs par instance : la limite réelle est multipliée par le nombre d'instances. | Compteurs partagés (Redis, Bucket4j). |
| Cache du catalogue | Une écriture du Gérant ne vide le cache que sur son instance. | Cache partagé (Redis) ou durée de vie courte. |
| Tâches planifiées (expiration des paiements, purge des clés, relais) | Lancées sur chaque instance. Sans risque fonctionnel (verrous en base) mais en double. | Verrou de tâche (ShedLock) ou une seule instance dédiée. |
| Photos sur disque local | Une photo déposée sur une instance est absente des autres. | Implémentation `PhotoStorage` sur stockage objet (voir « Reste à faire »). |

Déjà compatibles plusieurs instances : JWT, idempotence, outbox, jeton de suivi, audit, verrous (tout est en
base).
