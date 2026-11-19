# Personnaliser SenYummies pour un restaurant

Ce guide explique comment livrer l'App Client et le back-office Manager à un nouveau restaurant, **sans toucher au code**. Tout part d'un seul dossier de configuration et d'une seule commande :

```sh
npm run client -- <chemin-ou-identifiant-du-restaurant>
```

Les images Docker sont **les mêmes pour tous les restaurants**. La marque (nom, couleurs, logo, textes) et les données initiales (menu, zones, comptes) sont lues au démarrage des conteneurs, jamais compilées. Une mise à jour des images profite donc à tous les clients.

Sans configuration, la marque par défaut SenYummies s'applique comme avant.

---

## 1. Ce qu'il faut demander au restaurant

| Élément | Format attendu | Remarques |
|---|---|---|
| **Nom** du restaurant | Texte | Plus, si besoin, un nom court (12 caractères au plus) pour l'icône du téléphone |
| **Slogan** | Une phrase | Une virgule coupe le slogan en deux lignes sur l'accueil |
| **Nom sur les tickets** | Texte | Ex. « Kër Saveurs — Thiès » |
| **Couleur** principale | Code `#RRGGBB` | Facultatif : une couleur foncée (boutons) et une couleur claire (accents sur fond sombre). Sinon, elles sont calculées |
| **Logo** | **SVG** de préférence, sinon PNG carré de **512 × 512 px minimum**, fond transparent ou plein | Obligatoire. Utilisé dans l'en-tête, le splash et les reçus |
| Monogramme | SVG ou PNG carré ≥ 512 px | Facultatif : version compacte pour les icônes de l'app et l'onglet. Sinon, le logo |
| Icônes PWA | PNG 192, 512, maskable 512, apple-touch 180 | Facultatif : générées automatiquement depuis le monogramme |
| **Bannière** d'accueil | Photo paysage, **1600 × 900 px** conseillé (1200 × 500 minimum) | Facultatif. Recompressée automatiquement |
| Fond du splash | Photo portrait, **1080 × 1920 px** conseillé | Facultatif. Sinon, un dégradé de la couleur |
| Photos de connexion du Manager | 1 à 3 photos paysage, 1600 px | Facultatif |
| **Menu** | Catégories, produits (nom, description, prix en F CFA entiers), accompagnements et condiments, plats du jour par jour et par service | Un tableur suffit : on le recopie dans les fichiers JSON |
| **Photos des plats** | JPEG, PNG ou WebP, **2 Mo maximum**, 800 px de large conseillé | Les photos sont nettoyées (EXIF, GPS) à l'import |
| **Coordonnées** | Téléphone et e-mail du support, adresse, WhatsApp, Instagram, Facebook, TikTok, site | Le téléphone au format international (`+221…`) |
| **Horaires** | Horaires d'ouverture par jour, et horaires des services déjeuner et dîner | Les horaires d'ouverture sont **affichés** seulement ; les services pilotent les plats du jour |
| **Paiements** | Wave, Orange Money, Mixx by Yas, carte, espèces au comptoir : oui ou non pour chacun | Au moins un moyen en ligne |
| **Modes** | Livraison, retrait au comptoir | |
| **Zones de livraison** | Région, quartier ou arrondissement, frais en F CFA | Les arrondissements officiels sont reconnus automatiquement |
| **Personnel** | Pour chaque personne : prénom, nom, rôle (Manager, Gérant, Cuisine, Livreur, Vendeur), téléphone facultatif | **Aucun mot de passe** : il est généré au déploiement |
| **Domaines** | Ex. `commande.restaurant.sn` et `gestion.restaurant.sn`, plus un e-mail technique pour les certificats HTTPS | |

---

## 2. Organiser le dépôt privé des restaurants

Les dépôts RestoApp sont **publics**. Les dossiers des vrais restaurants (menus, prix, personnel) vont dans un dépôt **privé** séparé, `Clients_RestoApp`, cloné à côté des autres :

```
Repos/
├── Backend_RestoApp/        (public : outil, schéma, clients/exemple)
├── Client_RestoApp/         (public)
├── Manager_RestoApp/        (public)
└── Clients_RestoApp/        (PRIVÉ)
    ├── README.md            qui contacter, état de chaque restaurant
    ├── .gitignore           deploiements/, .env.secrets, comptes-initiaux.txt
    ├── chez-fatou/
    │   ├── restaurant.json
    │   ├── assets/
    │   └── donnees/
    └── le-baobab/
        └── ...
```

Règles :

- **Un dossier par restaurant.** Son nom est l'`identifiant` de `restaurant.json`.
- **Jamais de secret** dans ce dépôt : ni mot de passe, ni clé de paiement, ni JWT. L'outil refuse un dossier qui en contient.
- Le `.gitignore` du dépôt privé contient au minimum :

  ```
  deploiements/
  .env.secrets
  comptes-initiaux.txt
  ```

- Pas de CI dans ce dépôt : la validation se fait avec la commande ci-dessous, avant chaque commit.

La commande accepte un chemin, ou un identifiant cherché dans `$CLIENTS_DIR` :

```sh
cd Backend_RestoApp
npm ci                                                  # une seule fois
npm run client -- ../Clients_RestoApp/chez-fatou        # par chemin
CLIENTS_DIR=../Clients_RestoApp npm run client -- chez-fatou   # par identifiant
npm run client -- exemple                               # clients/exemple de ce dépôt
```

---

## 3. Remplir le dossier

Partez de la copie de l'exemple complet : `clients/exemple/` (restaurant **fictif** Kër Saveurs, à Thiès).

```sh
cp -r Backend_RestoApp/clients/exemple Clients_RestoApp/chez-fatou
```

```
chez-fatou/
├── restaurant.json        identité, couleurs, coordonnées, horaires, paiements, modes, zones, déploiement
├── assets/                logo, monogramme, favicon, bannière, splash, photos de connexion
└── donnees/
    ├── categories.json
    ├── produits.json
    ├── accompagnements.json   (facultatif)
    ├── plats-du-jour.json     (facultatif)
    ├── annonces.json          (facultatif : annonces de lancement)
    ├── personnel.json         (facultatif : comptes initiaux, sans mot de passe)
    └── photos/
```

Dans un éditeur comme VS Code, la ligne `"$schema"` de `restaurant.json` donne l'**autocomplétion** et la description de chaque champ. Les schémas sont dans `outil-client/schema/`.

### Points d'attention

- **`identifiant` et les `id` des fiches ne changent plus une fois déployés.** C'est par eux que l'import reconnaît une fiche déjà créée. Renommer un `id`, c'est créer une nouvelle fiche.
- **Prix** : entiers en F CFA, sans espace ni décimale (`3500`, pas `3 500` ni `3500.5`).
- **Chemins** des images et photos : relatifs au dossier du restaurant (`donnees/photos/yassa.jpg`), sans espace ni accent. Une photo peut aussi être un lien `https://`.
- **Statut** d'une fiche : `ACTIF` (publié, par défaut), `BROUILLON` (préparé, invisible des clients) ou `ARCHIVE`.
- **Badge « Tendance »** d'un produit : `true` (toujours), `false` (jamais) ou `"auto"` (les plus commandés, par défaut).
- **Catégories** : `"accompagnements": true` propose les accompagnements et condiments pour les produits de la catégorie.
- **Zones** : `{ "region": "THIES", "secteur": "Thiès Nord", "frais": 500 }`. Un quartier qui n'est pas un arrondissement officiel est accepté s'il précise son `departement`. Avec `"masquerAutresZones": true` (par défaut), les arrondissements officiels non listés d'une région livrée sont masqués.
- **Personnel** : un seul `MANAGER`. Prévoyez au moins un `GERANT`, qui gère ensuite le menu depuis le Manager.

---

## 4. Lancer la commande

```sh
npm run client -- ../Clients_RestoApp/chez-fatou --verifier   # valide seulement
npm run client -- ../Clients_RestoApp/chez-fatou              # valide puis génère
```

| Option | Effet |
|---|---|
| `--verifier` | Valide sans rien générer |
| `--forcer` | Au prochain démarrage, **réimpose** le catalogue du dossier (voir § 6) |
| `--dev` | Écrit aussi `dev/.env.local` (variables `VITE_MARQUE_*`) pour `npm run dev` |
| `--sortie <dossier>` | Génère ailleurs que dans `deploiements/<identifiant>/` |

### Les erreurs

Toutes les erreurs sont listées d'un coup, avec le fichier et le champ en cause :

```
✖ 5 erreur(s) à corriger :
  1. restaurant.json › couleurs › principale : couleur invalide : format #RRGGBB attendu (ex. #A6192E)
  2. restaurant.json › livraison › zones[0] « Thiès Nord » : zone sans frais : indiquez les frais de livraison (F CFA, 0 si gratuite)
  3. donnees/produits.json › [0] « poisson-braise » › prix : prix négatif ou nul : un produit vendu coûte au moins 1 F CFA
  4. donnees/produits.json › [3] « aiguillettes » › photo : photo introuvable : donnees/photos/aiguilettes.jpg
  5. restaurant.json › livraison › zones[5] « Thies Nrd » : « Thies Nrd » n'est pas un arrondissement officiel de THIES : précisez son departement pour en faire une zone du restaurant (vouliez-vous : Thiénaba, Thiès Nord, Thiès Sud ?)
```

Le validateur détecte aussi :

- un logo absent ou trop petit ;
- une photo de plus de 2 Mo ;
- une catégorie inconnue ;
- un `id` en double ;
- deux comptes Manager ;
- l'absence de moyen de paiement en ligne ;
- un champ inconnu (faute de frappe) ;
- un JSON mal formé (avec ligne et colonne) ;
- **tout champ qui ressemble à un mot de passe ou à une clé**.

### Ce qui est généré

Dans `deploiements/<identifiant>/` (ignoré par git) :

| Fichier | Contenu |
|---|---|
| `.env` | Marque (`MARQUE_*`), support, domaines, moyens de paiement et modes. **Aucun secret** |
| `.env.secrets.exemple` | Secrets à fournir : MySQL, JWT, prestataire de paiement |
| `docker-compose.yml` | Production : HTTPS (Caddy), MySQL, sauvegardes nocturnes, images communes |
| `docker-compose.local.yml` | Essai sur le poste, sans HTTPS, paiement simulé |
| `marque/` | Logo, bannière, splash, photos de connexion, favicon, icônes PWA générées |
| `donnees/` | `import.json` et photos, lus par le backend au démarrage |
| `comptes-initiaux.txt` | Mots de passe **temporaires** du personnel. **Confidentiel, jamais commité** |
| `LISEZMOI.md` | Commandes de démarrage de ce restaurant |

---

## 5. Vérifier en local

```sh
cd deploiements/chez-fatou
cp .env.secrets.exemple .env.secrets     # remplir ; PAIEMENT_PRESTATAIRE=simule pour l'essai
docker compose --env-file .env --env-file .env.secrets \
  -f docker-compose.yml -f docker-compose.local.yml up -d
docker compose logs backend | grep "Import du restaurant"
#  -> Import du restaurant chez-fatou (creation seule) : 41 créée(s), 0 réimposée(s), 0 conservée(s)
```

Pour essayer une version précise des images : `VERSION=v1.4.0 docker compose …`.

Ouvrez `http://localhost:8081` (App Client) et `http://localhost:8082` (Manager), puis vérifiez :

- [ ] **Onglet et app installée** : nom, favicon, couleur de la barre du navigateur, icône sur l'écran d'accueil.
- [ ] **Splash et accueil** : logo, nom en deux tons, slogan, bannière, textes d'accueil.
- [ ] **Menu** : catégories, produits, photos, prix, badge Tendance, rupture de stock, plats du jour aux bons jours et services.
- [ ] **Commande** : seuls les modes et moyens de paiement proposés apparaissent ; zones et frais justes.
- [ ] **« Besoin d'aide ? »** : téléphone, e-mail, WhatsApp, réseaux, adresse, horaires.
- [ ] **Manager** : photos de connexion. Chaque compte de `comptes-initiaux.txt` se connecte et doit choisir un nouveau mot de passe.
- [ ] **Tickets et reçus** : nom de l'établissement, logo, adresse, téléphone. Les reçus s'impriment ou se sauvegardent en PDF depuis la caisse et l'espace Livreur.

---

## 6. Déployer et mettre à jour

### Première mise en ligne

1. **DNS** : les deux domaines pointent vers le serveur, ports 80 et 443 ouverts.
2. **Images communes** sur le serveur, au tag choisi (`deploiement.versionImages`, `latest` par défaut) : `senyummies/backend`, `senyummies/client`, `senyummies/manager`. Elles viennent du registre de SSD Consulting (variable `REGISTRE` du `.env`) ou d'une construction unique avec `deploiement/prod/docker-compose.yml`.
3. **Copie du dossier** sur le serveur, **sauf `comptes-initiaux.txt`**. Créez-y `.env.secrets` avec les vrais secrets et le prestataire de paiement réel : le profil prod refuse le paiement simulé.
4. **Démarrage** :

   ```sh
   docker compose --env-file .env --env-file .env.secrets up -d
   ```

5. **Comptes** : remettez à chaque personne son identifiant et son mot de passe temporaire en main propre, puis supprimez `comptes-initiaux.txt`.

### Mises à jour

| Changement | Ce qu'il faut faire |
|---|---|
| Nouvelle version des apps | Changer `VERSION` (ou `versionImages`), puis `docker compose … up -d`. Rien d'autre |
| Nouveau logo, couleur, texte | Modifier le dossier, relancer `npm run client`, recopier `.env` et `marque/`, puis `up -d` |
| Nouveaux produits dans le dossier | Relancer `npm run client`, recopier `donnees/`, puis `up -d` : **seules les fiches nouvelles sont créées** |
| Réimposer le menu du dossier | `npm run client -- … --forcer`, recopier `donnees/`, puis `up -d` |

### Règles de l'import

Elles sont garanties par la migration V9 et les tests :

- Un passage n'est appliqué **qu'une fois** : redémarrer les conteneurs ne refait rien.
- Une fiche déjà importée n'est **jamais recréée ni écrasée** : le Gérant a pu la modifier, l'archiver ou la supprimer depuis le Manager.
- `--forcer` réimpose les fiches du dossier, et recrée celles qui ont été supprimées. C'est à réserver à une remise à zéro voulue.
- Les **comptes du personnel ne sont jamais modifiés**, même avec `--forcer`. Si `comptes-initiaux.txt` a été perdu, réinitialisez le mot de passe depuis le Manager (page Personnel).
- Une erreur annule **tout** le passage. Le backend refuse alors de démarrer, avec le message en cause dans `docker compose logs backend`.

---

## 7. Secrets : où ils vont

Rien de ce qui suit n'est dans `clients/`, dans le dépôt privé ou dans git :

| Secret | Où |
|---|---|
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | `.env.secrets` sur le serveur |
| `JWT_SECRET` (32 caractères minimum) | `.env.secrets` |
| `PAIEMENT_PRESTATAIRE` et clés du prestataire | `.env.secrets` |
| `MANAGER_INITIAL_*` (si `personnel.json` n'a pas de Manager) | `.env.secrets`, à vider après le premier démarrage |
| Mots de passe temporaires du personnel | `comptes-initiaux.txt`, à remettre puis supprimer. Le backend ne reçoit que leur empreinte BCrypt |

---

## 8. Pour les développeurs

| Élément | Où |
|---|---|
| Outil | `outil-client/` (Node 20+) : `client.mjs`, `lib/` (validation, génération, images, import), `schema/`, `modeles/` |
| Tests de l'outil | `npm test` : schéma et messages, génération, comptes, alignement des zones officielles sur V6 |
| Côté apps | Variables `MARQUE_*`, lues par `src/config/marque.js` et écrites dans `/config.js` au démarrage par `docker/40-config-runtime.sh`. Les tests `configRuntime.test.js` vérifient titre, couleur, logo, manifeste et favicon |
| Côté backend | `restaurant/ImportRestaurant` (import idempotent, test `ImportRestaurantIT`) et `restaurant/OffreRestaurant`, qui refuse côté serveur un moyen de paiement ou un mode non proposé |
| Zones officielles | Après une modification de V6 : `node outil-client/reference/generer.mjs` |
| Mode développement | `npm run client -- exemple --dev`, puis copier `deploiements/exemple/dev/.env.local` et `marque/` dans `Client_RestoApp` et `Manager_RestoApp` (`public/marque/`, ignoré par git) |
