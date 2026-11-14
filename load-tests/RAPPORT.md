# Tests de charge : rapport du premier passage

- **Date** : 14/11/2026
- **Branches** : `feature/tests-de-charge` (mesures), `perf/cache-sans-transaction` (correction du goulot)
- **Environnement** : `deploiement/loadtest` (MySQL 8.0, backend en profil `loadtest`, nginx de l'App Client), base de démo neuve.

> **Ces chiffres ne sont pas des références.** Ils ont été mesurés sur un PC de développement Windows (12 threads, Docker Desktop) qui faisait tourner en même temps k6, nginx, le backend et MySQL. Ils servent à comparer des versions entre elles et à trouver les goulots. Les valeurs de référence viendront du serveur de recette, une fois l'hébergement choisi.

## En bref

- **Fonctionnel** : aucun défaut sous charge. 0 % d'erreurs HTTP sur tous les scénarios. Le total des commandes correspond toujours au catalogue. Il n'y a ni doublon (idempotence), ni commande acceptée après une rupture, ni double transition. Les transitions interdites renvoient 403 et la limitation renvoie 429 à partir de la 11ᵉ requête.
- **Décrochage** : la lecture du menu tient le seuil jusqu'à **300 utilisateurs simultanés** (p95 de 55 ms), puis décroche à **500** (p95 de 1,95 s, seuil de 300 ms dépassé). Le débit plafonne alors vers 950 requêtes/s.
- **Goulot** : sur ce PC, c'est d'abord le processeur, partagé par toute la chaîne. Le cache fonctionne (100 % de succès), mais **chaque lecture servie par le cache ouvrait quand même une transaction MySQL**. Cela coûtait une connexion du pool et quatre allers-retours vers la base, pour rien.
- **Corrigé** (piste 1, voir « Correction appliquée ») : à conditions identiques, MySQL passe d'environ 155 % à 10 % de processeur. À 500 utilisateurs, le débit gagne 39 % et la médiane passe de 1,6 s à 0,36 s.

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

## Correction appliquée : lectures en cache sans transaction (piste 1)

Branche `perf/cache-sans-transaction` :

- `@Transactional(readOnly = true)` est retiré de `CatalogueCachePublic`, `CatalogueService`, `AnnoncesPubliques` et `ZonesPubliques`, ainsi que des lectures publiques d'`AnnonceService` et de `ZonesService` ;
- la transaction de lecture est désormais ouverte à la demande (`LectureSeule`), seulement quand la base est vraiment lue : remplissage du cache ou vue du Gérant ;
- un test unitaire (`LecturesEnCacheSansTransactionTest`) vérifie qu'une lecture servie par le cache n'ouvre aucune transaction. Il échoue sur l'ancien code.

### Mesure avant / après

Les deux images ont été mesurées l'une après l'autre, deux fois chacune, en alternant avant / après / avant / après. Chaque mesure part d'une base neuve, après 30 s d'échauffement de la JVM, avec `lecture-menu.js` et un palier de 45 s :

| Utilisateurs | Version | Médiane | p95 | Débit | CPU backend | CPU MySQL |
|---:|---|---:|---:|---:|---:|---:|
| 300 | avant | 217 / 329 ms | 0,94 / 1,23 s | 613 / 572 req/s | ~420 % | ~155 % |
| 300 | **après** | **47 / 96 ms** | **0,24 / 0,55 s** | **749 / 714 req/s** | ~290 % | **~7 %** |
| 500 | avant | 1,58 / 1,63 s | 2,52 / 2,46 s | 648 / 642 req/s | ~390 % | ~160 % |
| 500 | **après** | **375 / 343 ms** | **2,02 / 1,69 s** | **871 / 918 req/s** | ~300 % | **~12 %** |

Chaque case donne les valeurs des deux mesures.

- **MySQL** est presque déchargé des lectures du menu : de ~270 000 à ~600 `SET autocommit` sur un passage.
- **Le backend** consomme moins de processeur pour plus de débit.
- **À 300 utilisateurs**, la médiane est divisée par 3 à 5 et le p95 par 2 à 4 ; il repasse sous le seuil de 300 ms dans une mesure sur deux.
- **À 500 utilisateurs**, le PC reste saturé (nginx, backend et k6 sur la même machine) : le p95 reste au-dessus du seuil.

Ces mesures A/B ont été prises en fin de journée, alors que d'autres applications tournaient sur le PC : environnements de dev et tunnels. Les valeurs absolues sont donc moins bonnes que celles du matin, données plus haut. Seule la comparaison avant / après, faite dans les mêmes conditions, compte.

## Pistes concrètes

### Lecture du menu (scénarios 1 et 2)

1. **Servir le cache sans transaction.** ✅ **Fait** (voir « Correction appliquée »). C'était la piste au meilleur rapport gain/effort.
   - Retirer `@Transactional(readOnly = true)` de `CatalogueCachePublic`, de `AnnoncesPubliques` et du chemin public de `CatalogueService`.
   - Garder une transaction en lecture seule uniquement sur le chargement réel, quand le cache est vide. Par exemple, une méthode privée appelée via `TransactionTemplate`, ou placer l'intercepteur de cache avant celui des transactions.
   - **Constaté** : le CPU de MySQL passe de ~155 % à ~10 %, et `SET autocommit` disparaît du haut de `performance_schema`. À 500 utilisateurs, le débit gagne 39 %.
2. **Cache nginx devant le menu public.** Le backend envoie déjà `Cache-Control: public, max-age=30` sur les lectures publiques, mais personne ne s'en sert entre le client et le backend. Un `proxy_cache` court dans le nginx des fronts, respectant cet en-tête, absorberait les rafales avant le backend. Les écrans du back-office restent en `no-store` et ne sont pas concernés.
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
