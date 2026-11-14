# Tests de charge (k6)

Ces scénarios mesurent la tenue de l'API SenYummies sous charge. Les requêtes passent toujours par le nginx de l'App Client, comme en production : `BASE_URL` = URL du front + `/api/v1`, jamais le port 8080 du backend.

Les premiers résultats et les pistes d'amélioration sont dans [RAPPORT.md](RAPPORT.md).

## Règles de sécurité

- **Liste blanche d'hôtes.** Un scénario ne vise que `localhost`, `127.0.0.1`, `host.docker.internal`, ou un hôte ajouté explicitement dans `ALLOWED_HOSTS` (liste séparée par des virgules). Toute autre cible arrête le script avant la première requête. L'hôte visé est affiché au démarrage.
- **Jamais la production.** N'ajoutez à `ALLOWED_HOSTS` qu'un serveur de recette. La production refuse de toute façon de démarrer avec le profil `loadtest` ou avec Actuator.
- **Paiement simulé uniquement.** Le compose de test force `APP_PAIEMENT_PRESTATAIRE=simule`.
- **Aucun secret dans les scripts.** Les mots de passe staff viennent des variables `K6_MDP_CUISINE`, `K6_MDP_GERANT`, `K6_MDP_MANAGER`, `K6_MDP_LIVREUR` et `K6_MDP_VENDEUR`. Les identifiants par défaut (`cuisine`, `gerant`…) peuvent être remplacés par `K6_ID_<ROLE>`.

## Les scénarios

| Fichier | Ce qu'il vérifie | Durée |
|---|---|---|
| `smoke.js` | 2 utilisateurs : menu, 1 commande, 1 transition Cuisine. Lancé en CI à chaque PR. | 30 s |
| `lecture-menu.js` | Lecture des 6 routes du menu (`?statut=ACTIF`) par paliers de 10, 50, 200 puis 500 utilisateurs. Affiche l'efficacité du cache (métriques Actuator). | ~5 min avec `PALIER=1m` |
| `passage-commande.js` | Parcours client réaliste : 50 % livraison, 50 % retrait, téléphones +221 variés, `Idempotency-Key` unique, total vérifié contre le catalogue lu, puis suivi avec le jeton. | ~3,5 min |
| `rush-midi.js` | Des clients commandent pendant que CUISINE, LIVREUR et VENDEUR font avancer les commandes jusqu'à LIVREE ou RETIREE. Un compte Cuisine interroge `GET /commandes` toutes les 500 ms (étiquette `groupe:sondage`) pour mesurer le délai d'apparition en Cuisine. Des transitions interdites doivent renvoyer 403. | ~3,5 min |
| `derniere-unite.js` | Rafale de commandes pendant que le GERANT passe un produit en RUPTURE. Une commande commencée après la rupture est refusée (400 `PRODUIT_INDISPONIBLE`), une commande acceptée avant reste PAYEE. Deux transitions simultanées donnent un seul succès et un 409. | ~45 s |
| `doublons.js` | La même commande envoyée 5 fois en parallèle avec la même clé ne crée qu'une commande, avec le même id partout. La même clé avec un autre contenu renvoie 409. | ~10 s |
| `limitation-debit.js` | **À lancer à part, sur un backend en profil dev normal.** Au-delà de 10 commandes ou 10 connexions par minute : 429 et `Retry-After`. Attendre une minute entre deux lancements. | ~2 s |

`lib/commun.js` regroupe la configuration, la liste blanche, la connexion staff, la lecture du catalogue, les téléphones, le calcul du total et la lecture des métriques.

### Seuils

| Mesure | Seuil |
|---|---|
| `http_req_failed` | < 1 % |
| `checks` | > 99 % |
| p95 de la lecture du menu (`groupe:menu`) | < 300 ms |
| p95 de `POST /commandes` | < 800 ms |

Certains codes sont attendus et ne comptent pas comme des échecs : 409 dans `derniere-unite.js` et `doublons.js`, 400 `PRODUIT_INDISPONIBLE` dans `derniere-unite.js`, 403 pour les transitions interdites de `rush-midi.js`, et 429 dans `limitation-debit.js`.

## Lancer un passage

### 1. Démarrer l'environnement de test

Le compose de test démarre MySQL, le backend en profil `loadtest` et le nginx de l'App Client. Il attend `Client_RestoApp` à côté de ce dépôt, ou le chemin indiqué dans `CLIENT_DIR`.

```bash
cd deploiement/loadtest
cp .env.exemple .env          # renseigner des valeurs JETABLES
docker compose up -d --build --wait
```

Une fois la pile démarrée :

- **API via nginx** : http://localhost:8380/api/v1
- **Métriques** : http://localhost:9190/actuator/prometheus (CPU, mémoire, pool Hikari, cache, requêtes SQL). Le port est publié sur 127.0.0.1 uniquement et nginx ne le relaie jamais.
- **Base** : catalogue de démo (27 produits, région Dakar ouverte) et comptes staff créés au démarrage avec les mots de passe du `.env`.

Le profil `loadtest` relève les limites de débit pour toutes les IP : sans cela, un seul poste de test serait bloqué à 10 commandes par minute.

### 2. Lancer un scénario

Avec Docker, sans rien installer, depuis `load-tests/` :

```bash
./lancer.sh smoke
PALIER=2m ./lancer.sh lecture-menu
PALIERS=10,30,60 ./lancer.sh rush-midi
```

`lancer.sh` utilise l'image `grafana/k6:1.3.0`. Il lit les mots de passe dans `deploiement/loadtest/.env` (ou dans `ENV_FILE`) sans les afficher. Il vise `http://host.docker.internal:8380/api/v1`. Les arguments après le nom du scénario sont passés à `k6 run`, par exemple `-q`.

Avec k6 installé sur la machine :

- **Windows** : `winget install k6 --source winget`
- **macOS** : `brew install k6`
- **Linux** : voir https://grafana.com/docs/k6/latest/set-up/install-k6/

```bash
BASE_URL=http://localhost:8380/api/v1 \
METRIQUES_URL=http://localhost:9190/actuator/prometheus \
K6_MDP_CUISINE=... k6 run smoke.js
```

### Variables

| Variable | Rôle | Défaut |
|---|---|---|
| `BASE_URL` | URL de l'API via nginx | `http://localhost:8380/api/v1` |
| `ALLOWED_HOSTS` | hôtes autorisés en plus de la liste blanche | aucun |
| `METRIQUES_URL` | métriques Prometheus, lues en fin de test | aucune (`lancer.sh` la renseigne) |
| `PALIER` | durée de chaque palier | `1m` |
| `PALIERS` | nombre d'utilisateurs par palier | selon le scénario |
| `DEBIT`, `PRODUIT_ID` | `derniere-unite.js` : commandes par seconde, produit visé | `20`, premier produit en stock |
| `ITERATIONS` | `doublons.js` : nombre de séries de 5 envois | `30` |
| `LIMITE` | `limitation-debit.js` : limite attendue par minute | `10` |

### 3. Pendant et après le passage

Ces relevés complètent les chiffres de k6 :

```bash
docker stats senyummies-loadtest-backend-1 senyummies-loadtest-mysql-1 senyummies-loadtest-client-1

# requêtes SQL de plus de 100 ms
docker compose exec mysql cat /var/lib/mysql/lent.log

curl -s http://localhost:9190/actuator/prometheus | grep -E '^(cache_gets|hikaricp_connections|process_cpu)'
```

Les rapports sont écrits dans `resultats/`, qui n'est pas versionné :

- `<scénario>-<date>.html` : le tableau de bord k6, à ouvrir dans un navigateur ;
- `<scénario>-<date>.json` : le résumé chiffré.

### 4. Repartir de zéro

```bash
cd deploiement/loadtest && docker compose down -v
```

Faites-le entre deux passages : chaque mesure doit partir d'une base neuve.

## En CI

Le job `smoke` de `.github/workflows/backend-ci.yml` lance `smoke.js` à chaque PR, en moins de 3 minutes, sur toute la chaîne : nginx de `Client_RestoApp` (version fixée par `CLIENT_REF`), backend en profil `loadtest` et MySQL. Les gros scénarios restent manuels.

## Lire les chiffres

Les mesures prises sur un PC de développement ne sont **pas des références** : la machine fait tourner en même temps k6, nginx, le backend et MySQL. Les valeurs de référence viendront du serveur de recette, une fois l'hébergement choisi.
