# Tests de charge : rapport du premier passage

**Date** : 14/11/2026
**Branche** : `feature/tests-de-charge`
**Environnement** : `deploiement/loadtest` (MySQL 8.0, backend en profil `loadtest`, nginx de l'App Client), base de démo neuve.

> **Ces chiffres ne sont pas des références.** Ils ont été mesurés sur un PC de développement Windows (12 threads, Docker Desktop) qui faisait tourner en même temps k6, nginx, le backend et MySQL. Ils servent à comparer des versions entre elles et à trouver les goulots. Les valeurs de référence viendront du serveur de recette, une fois l'hébergement choisi.

## En bref

- **Fonctionnel** : aucun défaut sous charge. 0 % d'erreurs HTTP sur tous les scénarios. Le total des commandes correspond toujours au catalogue. Il n'y a ni doublon (idempotence), ni commande acceptée après une rupture, ni double transition. Les transitions interdites renvoient 403 et la limitation renvoie 429 à partir de la 11ᵉ requête.
- **Décrochage** : la lecture du menu tient le seuil jusqu'à **300 utilisateurs simultanés** (p95 de 55 ms), puis décroche à **500** (p95 de 1,95 s, seuil de 300 ms dépassé). Le débit plafonne alors vers 950 requêtes/s.
- **Goulot** : sur ce PC, c'est d'abord le processeur, partagé par toute la chaîne. Le cache fonctionne (100 % de succès), mais **chaque lecture servie par le cache ouvre quand même une transaction MySQL**. Cela coûte une connexion du pool et quatre allers-retours vers la base, pour rien.

## Résultats par scénario

### 1. `smoke.js` (2 utilisateurs, 30 s)

Tous les contrôles passent :

- p95 du menu : 41 ms ;
- p95 de `POST /commandes` : 82 ms ;
- 0 % d'erreurs.

Le même scénario tourne en CI à chaque PR, dans le job `smoke`.

### 2. `lecture-menu.js` (6 routes du menu, `?statut=ACTIF`)

Un palier par mesure, 45 s chacun, sur une même base :

| Utilisateurs | Requêtes/s | p95 du menu | Seuil (< 300 ms) |
|---:|---:|---:|---|
| 50 | 131 | 23 ms | ✓ |
| 100 | 265 | 25 ms | ✓ |
| 200 | 522 | 26 ms | ✓ |
| 300 | 780 | 55 ms | ✓ |
| 500 | 948 | 1 950 ms | ✗ |

Jusqu'à 300 utilisateurs, le débit suit la charge. À 500, il n'augmente plus que de 20 % alors qu'il en faudrait 65 %, et les temps sont multipliés par 35 : c'est la saturation.

**Efficacité du cache (métriques Actuator `cache.gets`)** : 100 % de succès sur les 7 caches (19 936 succès pour 1 échec par cache, le premier remplissage).

**Processeur pendant le passage en paliers 10 → 50 → 200 → 500** (moyenne relevée par `docker stats`, 100 % = 1 cœur) :

| Palier | Backend | MySQL | nginx |
|---:|---:|---:|---:|
| 50 | 50 % | 18 % | 11 % |
| 200 | 133 % | 66 % | 47 % |
| 500 | 308 % (pointe à 448 %) | 128 % | 209 % (pointe à 573 %) |

**Anomalie** : MySQL monte à plus d'un cœur alors que le cache répond à toutes les lectures. Le résumé `performance_schema` l'explique. Pour environ 120 000 lectures du menu, MySQL a reçu :

- environ 270 000 `SET autocommit` ;
- environ 125 000 `SET SESSION TRANSACTION READ ONLY` puis `READ WRITE` ;
- environ 135 000 `COMMIT`.

Ce sont les quatre premières requêtes en temps cumulé, loin devant les vraies requêtes. En cause :

- `CatalogueService` et `CatalogueCachePublic` portent `@Transactional(readOnly = true)` au niveau de la classe ;
- la transaction est donc ouverte avant que le cache soit consulté, puis validée à vide.

### 3. `passage-commande.js` (10 → 25 → 50 utilisateurs)

- **Temps** : p95 de `POST /commandes` à 53 ms (seuil : 800 ms), p95 du menu à 16 ms.
- **Contrôles** : 100 % réussis sur 892 commandes, moitié livraison, moitié retrait. Sous-total, frais de livraison et total correspondent toujours au catalogue lu.

### 4. `rush-midi.js` (10 → 30 → 60 clients, plus Cuisine, Livreur, Vendeur, observateur et intrus)

Passage sur une base neuve :

- **Commandes** : 812 menées jusqu'à LIVREE ou RETIREE ; p95 de `POST /commandes` à 55 ms ; 0 % d'erreurs.
- **Délai d'apparition en Cuisine** : médiane de 270 ms, p95 de 514 ms. Ce délai inclut l'intervalle de 500 ms entre deux interrogations : la commande est visible dès sa validation. Les interrogations elles-mêmes (`groupe:sondage`) ont un p95 de 24 ms.
- **Transitions interdites** : 403 dans tous les cas (VENDEUR → EN_LIVRAISON, CUISINE → LIVREE).
- **Requêtes lentes** : le journal de MySQL (> 100 ms) ne contient que 3 requêtes, toutes des `INSERT INTO evenements_sortants`. Le temps vient de 0,1 s d'attente de verrou, pas de l'exécution.

Un premier passage enchaîné sans remise à zéro avait donné un délai de plusieurs minutes. C'était un artefact de mesure, pas un défaut : la file staff (500 commandes au plus, les plus récentes d'abord) contenait encore les ~900 commandes du scénario précédent. Depuis, l'observateur ne mesure que les commandes créées pendant le scénario, et chaque passage repart d'une base neuve (`docker compose down -v`).

### Autres scénarios

- **`derniere-unite.js`** (20 commandes/s pendant 30 s, passage en RUPTURE à 10 s) :
  - 400 commandes refusées avec `PRODUIT_INDISPONIBLE` ;
  - aucune commande acceptée après que le client a vu la rupture ;
  - 20 paires de transitions simultanées, chacune avec un seul succès et un 409 `STATUT_DEJA_APPLIQUE`.
- **`doublons.js`** :
  - 5 envois parallèles avec la même clé donnent une seule commande, avec le même id ;
  - la même clé avec un autre contenu donne 409 `IDEMPOTENCE_CONFLIT`.
- **`limitation-debit.js`** (backend avec les limites normales) :
  - `POST /commandes` : 10 × 400 puis 3 × 429 ;
  - `POST /auth/login` : 10 × 401 puis 3 × 429 ;
  - le code `TROP_DE_REQUETES` et l'en-tête `Retry-After` sont présents.

## Pistes concrètes

### Lecture du menu (scénarios 1 et 2)

1. **Servir le cache sans transaction.** C'est la piste au meilleur rapport gain/effort.
   - Retirer `@Transactional(readOnly = true)` de `CatalogueCachePublic`, de `AnnoncesPubliques` et du chemin public de `CatalogueService`.
   - Garder une transaction en lecture seule uniquement sur le chargement réel, quand le cache est vide. Par exemple, une méthode privée appelée via `TransactionTemplate`, ou placer l'intercepteur de cache avant celui des transactions.
   - **Gain attendu** : plus de connexion Hikari ni d'aller-retour MySQL par lecture servie par le cache, soit environ un cœur de MySQL libéré à 500 utilisateurs, et du processeur côté backend.
   - **À vérifier** : relancer `lecture-menu.js` ; `SET autocommit` doit disparaître du haut de `performance_schema`.
2. **En-têtes de cache HTTP sur le menu public.** Un `Cache-Control: public, max-age=30` sur les six routes du menu (le catalogue change rarement), avec un `proxy_cache` court dans nginx, absorberait les rafales avant le backend. Les écrans du back-office ne sont pas concernés.
3. **nginx.** Il consomme jusqu'à 5,7 cœurs à 500 utilisateurs, surtout pour recompresser en gzip, à chaque requête, des réponses identiques. Le `proxy_cache` de la piste 2 éviterait une partie de ce travail. Sur le serveur de recette, mesurer nginx sur une machine séparée de k6.

### Rush de midi (scénario 4)

4. **Pool Hikari de 10 connexions** (valeur par défaut). Il a tenu ici, mais il sera le premier plafond quand plusieurs postes staff interrogeront la base en même temps qu'un pic de commandes.
   - **À mesurer en recette** : `hikaricp_connections_pending` et `hikaricp_connections_acquire_seconds` pendant `rush-midi.js` avec plus de clients.
   - **Réglage** : `spring.datasource.hikari.maximum-pool-size`, cohérent avec le nombre de cœurs de la base.
   - La piste 1 libère déjà la plupart des emprunts de connexion inutiles.
5. **Interrogations toutes les 500 ms.** Elles coûtent peu (p95 de 24 ms), mais elles se multiplient avec le nombre de postes. Le Manager utilise déjà le flux SSE partagé ; l'interrogation doit rester un secours, à un intervalle plus long.
6. **Attente de verrou sur `evenements_sortants`** (0,1 s dans les cas lents). À surveiller quand le volume augmentera, par exemple avec la taille des lots du publieur ou un index sur `publie_le`. Aucun impact mesurable aujourd'hui.

### Méthode

7. **Prochain passage, sur le serveur de recette** :
   - k6 lancé depuis une autre machine ;
   - même jeu de scénarios ;
   - comparaison avant et après la piste 1.

   Ce passage fixera les valeurs de référence.

## Fichiers du passage

Les rapports sont dans `load-tests/resultats/`, qui n'est pas versionné :

- les rapports HTML et JSON de k6 ;
- `docker-stats.csv` ;
- les métriques Prometheus relevées après chaque scénario ;
- `mysql-lent.log`, le journal des requêtes lentes.
