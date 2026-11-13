# SenYummies : reste à faire

Pour l'équipe (backend et fronts). État au 13 novembre 2026.

## Où on en est

Tout ce qui ne dépend pas d'une décision du client est développé et branché :

- **Backend** :
  - migrations Flyway ;
  - catalogue, annonces, zones (zones officielles présentes dans toute base), photos ;
  - commandes, suivi client sécurisé, transitions par rôle ;
  - temps réel SSE ;
  - paiement **simulé** (init, webhook signé, expiration) ;
  - journal d'audit, statistiques ;
  - premier compte Manager en production.
- **App Client** branchée sur le backend :
  - idempotence ;
  - statut décidé par le serveur ;
  - jeton de suivi ;
  - paiement par redirection (plus aucun formulaire de carte).
- **SenYummies Manager** branché sur le backend :
  - menu, plats du jour, accompagnements et annonces, avec envoi des photos ;
  - zones ;
  - temps réel en Cuisine, Livreur et Retraits ;
  - ventes comptoir ;
  - audit, statistiques.
- **Déploiement** :
  - compose de démonstration (`deploiement/demo`) ;
  - compose de **production** (`deploiement/prod`) : Caddy HTTPS, MySQL sauvegardé chaque nuit.
- **Qualité et sécurité** : CI verte sur les trois dépôts. Elle couvre :
  - lint et tests ;
  - audit des dépendances ;
  - SAST : CodeQL, Semgrep, Trivy ;
  - DAST : ZAP sur l'API.

Documents liés :

- [`tableau-10-points.md`](tableau-10-points.md) : chaque fonctionnalité face aux 10 points de vigilance ;
- [`../contrat-api/ecarts-contrat.md`](../contrat-api/ecarts-contrat.md) : ce qui diffère d'`openapi.yaml`, à faire valider par le lead Backend ;
- [`../contrat-api/openapi-implemente.json`](../contrat-api/openapi-implemente.json) : description OpenAPI **générée par le backend** (exacte par construction), à comparer au contrat. Régénérer avec `scripts/contrat/exporter-openapi.sh` ;
- [`../deploiement/demo/README.md`](../deploiement/demo/README.md) : toute l'application en une commande ;
- [`../deploiement/prod/README.md`](../deploiement/prod/README.md) : mise en production.

Reste à faire, dans l'ordre :

1. **Les 3 décisions du client** (§ 1) : prestataire de paiement, hébergement, vrai menu.
2. **Validation du contrat** par le lead Backend (§ 3, point 1).
3. **Décision produit** : « supprimer » une fiche dans le Manager = l'archiver (§ 2).

---

## 1. Les 3 décisions en attente

### 1.1 Prestataire de paiement (décision du PDG)

**Aujourd'hui :**

- `SimulatedPaymentProvider` répond à la place d'un vrai prestataire. Il est marqué « PAIEMENT SIMULÉ » dans les logs, les réponses et Swagger.
- Il est désactivé d'office en profil `prod`, et le backend refuse de démarrer en prod si `APP_PAIEMENT_PRESTATAIRE=simule`.
- Les routes `/paiements/init` et `/paiements/webhook` existent déjà : signature vérifiée, traitement une seule fois, expiration au bout de 30 min.

**Le jour où le prestataire est choisi** (PayDunya ou API directes Wave / Orange Money…) :

1. **Obtenir les clés de test** du prestataire et sa documentation de notification (IPN / webhook) :
   - format de la notification ;
   - méthode de signature ;
   - adresses IP d'envoi s'il en publie.

2. **Écrire une classe** dans `src/main/java/sn/chezketchup/backend/paiements/infrastructure/`, par exemple `PayDunyaPaymentProvider`, qui implémente `PaymentProvider`. Annotations : `@Component` et `@ConditionalOnProperty(name = "app.paiement.prestataire", havingValue = "paydunya")`. Méthodes :

   | Méthode | Ce qu'elle fait |
   |---|---|
   | `code()` | `"paydunya"` |
   | `estSimule()` | `false` |
   | `initier(DemandePaiement)` | Crée la transaction chez le prestataire (montant en F CFA entiers, moyen, téléphone). Renvoie `ResultatInitiation(reference, redirectUrl, EN_ATTENTE)`. Ne jamais renvoyer `PAYE` ici : seule la notification fait foi. |
   | `lireNotification(corps, entetes)` | Vérifie la signature selon la documentation du prestataire, sinon lève `SignatureInvalideException` (→ 401). Puis traduit en `NotificationPaiement(evenementId, reference, statut, montant)`. |

   Le reste ne change pas : `SuiviPaiements` gère déjà l'idempotence, le contrôle du montant, les verrous, l'expiration et le cas « payé après annulation ».

3. **Ajouter les propriétés** dans `application.properties`, toutes lues depuis l'environnement, sans valeur par défaut pour les secrets : `app.paiement.paydunya.cle-principale=${APP_PAIEMENT_PAYDUNYA_CLE_PRINCIPALE}`, etc.

4. **Renseigner les variables** sur le serveur :

   | Variable | Valeur |
   |---|---|
   | `APP_PAIEMENT_PRESTATAIRE` | `paydunya` |
   | Clés du prestataire | test d'abord, production ensuite |
   | URL de notification | `https://<domaine-client>/api/v1/paiements/webhook`, à déclarer chez le prestataire |
   | URL de retour | page Mes commandes de l'App Client |

5. **Tests à écrire, puis à lancer :**
   - **Unitaires** : vérification de signature (bonne, mauvaise, horodatage trop vieux) et traduction des statuts. Utiliser des exemples de notifications copiés de la documentation.
   - **Intégration** : sur le modèle de `PaiementsApiIT` (mode `webhook`), avec le prestataire bouchonné par WireMock (dépendance de test à ajouter).
   - **Bout en bout avec les clés de test** :
     - le backend doit être joignable en HTTPS public pour recevoir la notification (serveur de recette ou tunnel) ;
     - payer, refuser, abandonner (expiration), recevoir deux fois la même notification.
   - `mvn -B verify`.

6. **Remboursements.** Aujourd'hui, une commande payée puis annulée est seulement signalée (drapeau de remboursement + audit). Il faudra choisir entre :
   - un remboursement automatique par l'API du prestataire ;
   - une procédure manuelle avec un écran « à rembourser » dans le Manager.

7. **Contrat.** Mettre à jour `openapi.yaml` avec les écarts P.1 à P.4 validés.

8. **App Client** : déjà prête (lot 4). Elle suit `paiement.redirectUrl` et ne contient plus de formulaire de carte.

### 1.2 Hébergement de production

**Aujourd'hui :**

- Toute la configuration passe par des variables d'environnement.
- Les photos sont derrière l'interface `PhotoStorage`, implémentée sur disque local (volume Docker).
- Le compose de démonstration (`deploiement/demo/`) montre l'assemblage.
- Le profil `prod` bloque les réglages dangereux.

**Le jour où le serveur est choisi :**

1. **Serveur et domaines.** Un VPS avec Docker suffit au départ (une seule instance, voir § 3). Deux sous-domaines, par exemple `commande.<domaine>` pour l'App Client et `gestion.<domaine>` pour le Manager.

2. **HTTPS (obligatoire : la géolocalisation du navigateur ne marche qu'en HTTPS).**
   - Placer un reverse proxy devant les deux fronts. Caddy est recommandé : certificats Let's Encrypt automatiques.
   - Le backend et MySQL ne sont jamais exposés sur Internet.
   - Le proxy doit être sur le réseau Docker privé : le backend ne fait confiance à `X-Forwarded-For` / `X-Forwarded-Proto` que venant d'adresses privées.

3. ✅ **Fait : [`deploiement/prod/`](../deploiement/prod/README.md)**. Compose de production avec Caddy (HTTPS), MySQL et sauvegarde quotidienne, profil `prod`, journaux en rotation. Il reste à ajouter des limites mémoire adaptées au serveur choisi et, à terme, des images construites par la CI à la place de `build:`.

4. **Variables à renseigner :**

   | Variable | Rôle |
   |---|---|
   | `SPRING_DATASOURCE_URL` | Connexion MySQL |
   | `SPRING_DATASOURCE_USERNAME` | Utilisateur MySQL |
   | `SPRING_DATASOURCE_PASSWORD` | Mot de passe MySQL |
   | `APP_JWT_SECRET` | Secret JWT : 32 octets minimum, aléatoire, propre à la prod |
   | `APP_CORS_ALLOWED_ORIGINS` | Domaines exacts, sans joker. Inutile si tout passe par les nginx des fronts, mais à ne pas laisser sur `localhost`. |
   | `APP_PHOTOS_DOSSIER` | Dossier des photos, ou les variables du stockage objet (point 6) |
   | Variables du prestataire | Voir 1.1 |
   | `APP_LIMITE_*` | Seulement si les limites par défaut ne conviennent pas |

5. ✅ **Fait : premier compte MANAGER** (`ManagerInitial`, testé). Renseigner `MANAGER_INITIAL_IDENTIFIANT` et `MANAGER_INITIAL_MOT_DE_PASSE` (12 caractères minimum) au premier lancement, puis les vider. Le compte est créé seulement si aucun MANAGER n'existe, avec un mot de passe à changer à la première connexion, et l'action est tracée dans le journal d'audit.

6. **Photos sur stockage objet** (nécessaire dès qu'il y a plusieurs instances ou pour des sauvegardes simples) :
   - Écrire `ObjetPhotoStorage implements PhotoStorage` avec le SDK S3. Il couvre S3, Cloudflare R2 et MinIO.
   - L'activer par `@ConditionalOnProperty(name = "app.photos.stockage", havingValue = "objet")`, et passer `DisquePhotoStorage` en `matchIfMissing = true`.
   - Variables : point d'accès, bucket, clés.
   - `GET /photos/{nom}` peut continuer à servir les photos, ou rediriger vers une URL publique du bucket.
   - Test : MinIO en Testcontainers.
   - Les photos déjà sur disque sont à copier dans le bucket une fois.

7. ✅ **Fait : zones de livraison en production** (`V6__zones_officielles.sql`). Les 14 régions et 125 arrondissements officiels sont présents dans toute base, **toutes les régions fermées**. Le Gérant vérifie les prix (indicatifs) puis ouvre les régions livrées.

8. **MySQL :**
   - Utilisateur dédié aux droits limités au schéma.
   - Sauvegarde quotidienne : ✅ en place dans le compose de prod. À faire : la **copier hors du serveur** et **faire un essai de restauration**.
   - Fuseau : l'application calcule en `Africa/Dakar`.

9. **Vérifications de mise en ligne :**
   - le backend démarre en `prod` sans message de `VerificationConfigurationProd` ;
   - Swagger répond 404 ;
   - `GET /api/v1/health` répond 200 ;
   - une commande avec les clés de test du prestataire passe ;
   - la géolocalisation fonctionne sur un téléphone en 4G.

### 1.3 Le vrai menu

**Aujourd'hui :** le menu de démonstration (6 catégories, 27 produits, 9 plats du jour, 11 accompagnements, 5 annonces) est repris des fichiers du Manager. Il n'est chargé qu'en profil dev, et le script `scripts/demo/generer-donnees-demo.mjs` le régénère depuis le Manager.

**Le jour où le restaurant fournit son menu :**

1. **Méthode recommandée :** saisie par le Gérant dans le Manager (écrans Menu, Plats du jour, Accompagnements, Annonces), déjà branchés sur l'API. Aucun code, photos redimensionnées et nettoyées automatiquement.

2. **Pour un menu long :** un script d'import qui lit un tableur (CSV) et appelle l'API avec un compte Gérant. Dans l'ordre :
   1. `POST /categories` ;
   2. `POST /photos` ;
   3. `POST /produits` ;
   4. `PATCH /{id}/statut` → `ACTIF`.

   Le script passe par l'API et non par du SQL direct : les mêmes validations s'appliquent (prix entiers, URL d'image, catégories).

3. **Chaque catégorie** doit avoir `accompagnementPropose` réglé (par exemple les burgers). Les fronts ne doivent plus deviner à partir du nom.

4. **Photos :** JPEG, PNG ou WebP, 2 Mo maximum. Elles sont redimensionnées à 1 600 px et leurs métadonnées sont retirées.

5. **Les données de démo restent pour le dev.** Si le vrai menu doit aussi servir en dev, régénérer `db/demo` depuis le Manager mis à jour. `DonneesDemoIT` vérifie le chargement.

---

## 2. Lot 4 : fronts branchés sur le backend ✅

Fait et vérifié dans le navigateur contre le vrai backend (compose de démo). PR Client #25, Manager #42.

| | Ce qui a été fait |
|---|---|
| App Client | `Idempotency-Key`, statut décidé par le serveur, `accompagnementId`, `accompagnementPropose`, jeton de suivi mémorisé sur l'appareil, « Paiement en attente » avec « Payer maintenant », redirection vers la page du prestataire (https uniquement), **formulaire de carte retiré** (PCI DSS), 429 avec `Retry-After` |
| Manager, Gérant | Menu, plats du jour, accompagnements, annonces et zones sur l'API. Photos envoyées sur `POST /photos`. `version` envoyée : un conflit entre deux Gérants donne un **409 avec un message**, jamais un écrasement |
| Manager, Cuisine, Livreur, Retraits | Temps réel SSE (nouvelle commande affichée en environ 1 s), `version` sur les transitions, « déjà fait par un collègue » sur un 409 |
| Manager, Vendeur | Vente enregistrée par le serveur avant le reçu (ticket et monnaie rendue fournis par le serveur), `Idempotency-Key`, historique depuis le serveur |
| Manager, Manager | Tableau de bord (`/statistiques/tableau-de-bord`), journal d'audit (`/audit`) |

**Décision à valider : « supprimer » = archiver.** Le serveur ne supprime jamais une fiche, pour garder l'historique des commandes et le journal d'audit. Le bouton « Supprimer définitivement » a été retiré des archives du Manager. Si une vraie suppression est voulue (par exemple pour un brouillon jamais publié), il faudra :
- ajouter une route `DELETE` côté backend, refusée dès que la fiche est utilisée par une commande ;
- remettre le bouton côté Manager.

Le jour où le prestataire de paiement est branché (§ 1.1), l'App Client n'a **rien à changer** : elle suit déjà `paiement.redirectUrl`.

## 3. Points de vigilance

1. **Contrat d'API.** `openapi.yaml` n'a pas été modifié. Les ajouts sont listés dans `ecarts-contrat.md` (2.1–2.15, 3.1–3.13, P.1–P.4, 5.1–5.6), et la description générée par le backend est dans `openapi-implemente.json`. Le lead Backend les valide, puis le contrat passe en v0.4.0.
2. **Une seule instance du backend.** Le temps réel, la limitation de débit et le cache sont en mémoire. Le tableau des 10 points (point 10) liste ce qu'il faut changer avant d'en lancer une deuxième.
3. **Migrations.**
   - Ne jamais modifier une migration déjà appliquée : Flyway refuse au démarrage, car la somme de contrôle diffère.
   - Toujours ajouter un nouveau fichier `V<n>__...sql`.
   - Uniquement des ajouts. Une suppression de colonne se fait en deux versions : arrêter de l'utiliser, puis la supprimer dans la version suivante.
4. **Jamais le paiement simulé en production.** Le backend le refuse, mais ne pas contourner la vérification.
5. **Secrets.** `.env` n'est jamais commité. Un secret JWT ou un mot de passe affiché par erreur doit être changé.
6. **Données personnelles.** Nom, téléphone et position GPS :
   - jamais dans les logs ;
   - renvoyés au staff concerné et au client par son jeton seulement.

   Prévoir une durée de conservation (par exemple, effacer la position GPS des commandes livrées depuis plus de N mois) et vérifier les obligations de déclaration auprès de la CDP (Sénégal).
7. **Remboursements.** Une commande payée puis annulée est seulement signalée. Tant que 1.1 n'est pas traité, quelqu'un doit consulter le journal d'audit et rembourser à la main.
8. **Comptes.**
   - Premier MANAGER en prod : ✅ voir 1.2, point 5.
   - Les comptes de démo (seed) n'existent qu'en dev.
9. **Fins de ligne.** Sous Windows, `core.autocrlf=true`. Aucun diff qui réécrit tout un fichier : vérifier `git diff --stat` avant de committer.
10. **Workflow.** `develop` est protégé : une branche par sujet, une PR, CI verte (`mvn -B verify`). Toute modification d'`openapi.yaml` est signalée pour revue.
