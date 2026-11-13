# Démonstration complète en une commande

MySQL, backend et les deux fronts (App Client, SenYummies Manager), assemblés et démarrés ensemble.

## Prérequis

- Docker Desktop (ou Docker Engine + Compose v2).
- Les trois dépôts **côte à côte** dans le même dossier :

  ```
  Repos/
  ├── Backend_RestoApp/   ← ce dépôt
  ├── Client_RestoApp/
  └── Manager_RestoApp/
  ```

- Les fronts sur une branche qui contient le bloc nginx du temps réel
  (`location /api/v1/temps-reel/` dans `docker/nginx.conf.template`) : PR Client #23 et Manager #40,
  donc `develop` une fois ces PR fusionnées.

## Lancer

```bash
cd deploiement/demo
cp .env.exemple .env      # puis remplacer chaque "a-remplacer"
docker compose up -d --build
```

Le premier lancement construit les trois images (quelques minutes). Docker démarre les services dans l'ordre :
MySQL sain, puis backend sain (migrations Flyway appliquées), puis les deux fronts.

| Quoi | Adresse |
|---|---|
| App Client | http://localhost:8081 |
| SenYummies Manager | http://localhost:8082 |
| Swagger (depuis cette machine seulement) | http://localhost:8090/api/v1/swagger-ui.html |

Comptes du Manager : `cuisine`, `gerant`, `manager`, `livreur`, `vendeur`, avec les mots de passe
choisis dans `.env` (`MDP_*`).

Ports déjà pris ? Réglez `CLIENT_PORT`, `MANAGER_PORT` et `BACKEND_PORT` dans `.env`.

## Arrêter

```bash
docker compose down        # garde la base et les photos
docker compose down -v     # repart de zéro (efface la base et les photos)
```

## Ce que contient la démo

- **Profil `dev`** : menu, plats du jour, annonces et zones de démonstration (repris du Manager),
  Swagger actif.
- **PAIEMENT SIMULÉ** : aucune transaction réelle. Mode `immediat` par défaut (l'App Client actuelle
  fonctionne sans changement) ; `PAIEMENT_SIMULE_MODE=webhook` pour simuler un vrai prestataire
  (confirmation reçue plus tard par `/paiements/webhook`).
- **Une seule adresse par app** : chaque front relaie `/api/` vers le backend, et le flux temps réel
  (`/api/v1/temps-reel/commandes`, SSE) par un bloc nginx sans mise en mémoire tampon. Pas de CORS.
- MySQL n'est pas exposé hors du réseau Docker.

## Ce que ce n'est pas

Pas une installation de production : HTTP sans TLS (la géolocalisation du navigateur n'y marche que sur
`localhost`), comptes de démo, paiement simulé. La production est décrite dans
[`docs/reste-a-faire.md`](../../docs/reste-a-faire.md).

## Vérifié

Pile complète démarrée avec ce fichier : une vente comptoir passée via le nginx de l'App Client
(ticket 1001, monnaie rendue calculée par le serveur) est arrivée en direct sur le flux SSE ouvert
via le nginx du Manager (compte Cuisine).
