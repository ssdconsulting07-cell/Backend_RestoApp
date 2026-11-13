# SenYummies : reste à faire

Pour l'équipe (backend et fronts). État au 13 novembre 2026.

## Où on en est

Le backend de `develop` couvre les lots 1, 2, 3, paiement (simulé) et 5 :

- migrations Flyway et données de démo (profil dev) ;
- catalogue, annonces, zones, photos ;
- commandes, suivi client sécurisé, transitions par rôle ;
- temps réel SSE ;
- paiement simulé (init, webhook signé, expiration) ;
- journal d'audit ;
- statistiques.

`mvn -B verify` est vert (44 tests unitaires, 62 tests d'intégration sur MySQL réel). L'App Client actuelle passe une
commande de bout en bout contre ce backend, sans modification.

Documents liés :

- [`tableau-10-points.md`](tableau-10-points.md) : chaque fonctionnalité face aux 10 points de vigilance ;
- [`../contrat-api/ecarts-contrat.md`](../contrat-api/ecarts-contrat.md) : ce qui diffère d'`openapi.yaml`, à faire valider par le lead Backend ;
- [`../deploiement/demo/README.md`](../deploiement/demo/README.md) : toute l'application en une commande.

Pull requests encore ouvertes, à fusionner avant le lot 4 :

| Dépôt | PR | Objet |
|---|---|---|
| Client | #22 | Photo du jus de bissap |
| Client | #23 | Bloc nginx du temps réel (SSE) |
| Manager | #39 | Photo du jus de bissap |
| Manager | #40 | Bloc nginx du temps réel (SSE) |
| Backend | (cette PR) | Compose de démonstration et documents |

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

8. **App Client** : voir le lot 4 (redirection vers le prestataire, suppression du formulaire de carte).

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

1. **Méthode recommandée :** saisie par le Gérant dans le Manager (écrans Menu, Plats du jour, Accompagnements, Annonces), une fois le lot 4 branché. Aucun code, photos redimensionnées et nettoyées automatiquement.

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

## 2. Lot 4 : brancher les fronts sur le backend

Commun aux deux fronts :

- Démarrer les conteneurs avec `API_BASE_URL=/api/v1` et `API_UPSTREAM=http://<backend>:8080`. Le compose de démo le fait déjà.
- Le bloc nginx du temps réel est prêt (PR Client #23, Manager #40).
- Erreurs : toujours `{code, message, field}`. Afficher `message` au bon champ quand `field` est présent.
- Statuts à gérer :

  | Statut | Cas | Message à afficher |
  |---|---|---|
  | 409 | `VERSION_CONFLICT` | « modifié entre-temps, rechargez » |
  | 409 | `STATUT_DEJA_APPLIQUE` | Rafraîchir sans erreur |
  | 429 | Trop de requêtes | « Trop de tentatives, réessayez dans N s », d'après `Retry-After` |

- Montants en entiers (F CFA).
- Les identifiants sont des chaînes numériques (`"12"`) : ne pas dépendre du préfixe `produit-` des données de démo.

### App Client

| À faire | Détail |
|---|---|
| En-tête `Idempotency-Key` sur `POST /commandes` | Générer un UUID quand le client valide, le garder jusqu'à la réponse et **le renvoyer tel quel** en cas de nouvel essai (coupure réseau). Nouvelle clé seulement pour une nouvelle commande. |
| Ne plus envoyer `statut` ni `statutPaiement` | Ils sont ignorés : la commande naît `CREEE` / `en_attente`. Afficher « Paiement en attente », puis « Annulée » si le paiement expire. |
| Jeton de suivi | Mémoriser `jetonSuivi` (renvoyé **une seule fois** à la création) avec l'id de commande dans le `localStorage`. L'envoyer en `X-Jeton-Suivi` sur `GET /commandes/{id}` et `POST /paiements/init`. Sans jeton (autre appareil), seule la vue réduite par téléphone est disponible. |
| Paiement | **Supprimer le formulaire de carte** : un numéro de carte ne doit jamais être saisi sur notre page (PCI DSS). Rediriger vers `paiement.redirectUrl`. Au retour, relire la commande. Bouton « Réessayer le paiement » vers `POST /paiements/init`. Si `paiement.simule` est vrai, afficher un bandeau « paiement simulé ». |
| Lignes | Envoyer `accompagnementId` (aujourd'hui retrouvé par son nom, solution provisoire). |
| Accompagnement proposé | Lire `categorie.accompagnementPropose` au lieu de tester le nom « Burgers ». |

### SenYummies Manager

| À faire | Détail |
|---|---|
| Remplacer les contextes en mémoire | `MenuDataContext`, `AnnoncesContext` et `AccompagnementsCondimentsContext`, ainsi que les fichiers `*Data.js` (`menuData`, `platsDuJourData`, `accompagnementsCondimentsData`, `annonceData`, `zonesLivraisonData`), par des appels API. Toute modification renvoie la `version` lue, et le 409 est géré. Le cycle Brouillon / Actif / Archivé passe par `PATCH /{id}/statut`. |
| Photos | Envoi par `POST /photos` (champ `fichier`), puis enregistrement de l'URL renvoyée. |
| Cuisine, Livreur, Retraits comptoir | Déjà sur `apiGet` / `apiPatch` : vérifier les chemins, envoyer `version`, traiter `STATUT_DEJA_APPLIQUE` comme « déjà fait par un collègue ». Retirer `cuisineDemo.js` du chemin de production. |
| Temps réel | Remplacer le rafraîchissement périodique par le flux `GET /api/v1/temps-reel/commandes`. `EventSource` ne peut pas envoyer l'en-tête `Authorization` : utiliser `fetch` en lecture continue (ou `@microsoft/fetch-event-source`), renvoyer `Last-Event-ID` à la reconnexion et garder un rafraîchissement de secours. |
| Vente comptoir | `Idempotency-Key` comme l'App Client. Envoyer `montantRecu` et afficher `monnaieRendue` **calculée par le serveur**. |
| Écrans Manager | `auditData` → `GET /audit` et `/audit/export.csv` ; `commandesData` et `financeData` → `/statistiques/*` ; `ventesData` → `/statistiques/ventes-comptoir`. |

**Fini quand :** les fronts démarrés par le compose de démo fonctionnent sans aucune donnée locale. Une commande passée sur l'App Client apparaît **sans rechargement** en Cuisine. Deux Gérants qui modifient la même fiche obtiennent un message de conflit au lieu d'un écrasement.

---

## 3. Points de vigilance

1. **Contrat d'API.** `openapi.yaml` n'a pas été modifié. Les ajouts sont listés dans `ecarts-contrat.md` (2.1–2.15, 3.1–3.13, P.1–P.4, 5.1–5.6). Le lead Backend les valide, puis le contrat passe en v0.4.0 **avant** le lot 4.
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
