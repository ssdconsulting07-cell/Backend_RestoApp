# Backend_RestoApp — SenYummies (équipe Backend)

API commune consommée par `Client_RestoApp` (App Client) et `Manager_RestoApp` (SenYummies Manager, back-office). Stack : Spring Boot 3, Java 17, MySQL, Spring Security + JWT.

## Démarrage rapide

**Option recommandée (backend + MySQL ensemble) :**

```
cp .env.example .env      # remplir les valeurs (mot de passe MySQL, secret JWT...)
docker compose up --build
```

Le backend est alors sur `http://localhost:8080/api/v1`, Swagger UI sur `http://localhost:8080/api/v1/swagger-ui.html`.

> Le projet Docker Compose est nomme explicitement `senyummies` (champ `name:` dans `docker-compose.yml`) pour ne jamais entrer en collision avec un autre projet local. Ne pas relancer avec `-p code` (ou un autre nom generique partage) : cela peut arreter/supprimer les containers d'un projet different portant le meme nom.

**Option locale (sans Docker), prérequis JDK 17 + Maven :**

```
mvn spring-boot:run
```

Il faut alors une base MySQL déjà accessible en local — configurer `src/main/resources/application.properties`, ou pointer sur celle lancée par `docker compose up mysql`.

## Organisation — par fonctionnalité

Chaque paquet ci-dessous regroupe son propre contrôleur, service, repository, entité et DTO (pas de découpage par couche technique global) :

- `auth/` — connexion (`POST /auth/login`), émission du JWT
- `produits/` — menu (consultation publique + gestion par le Gérant)
- `commandes/` — création en mode invité, suivi, changement de statut par le staff
- `paiements/` — initialisation du paiement et webhook prestataire

Paquets transverses (infrastructure partagée, pas une fonctionnalité en soi) :

- `config/` — configuration Spring (sécurité, CORS pour les 2 frontends, etc.)
- `security/` — authentification JWT du staff SenYummies Manager (l'App Client reste toujours anonyme)
- `web/` — format d'erreur API standard (`ApiError`) et son gestionnaire global
- `payment/` — abstraction de paiement (`PaymentProvider`), pour brancher PayDunya au départ puis Wave/Orange Money en direct plus tard sans tout réécrire

## Le contrat d'API — comment les autres équipes y accèdent

`contrat-api/openapi.yaml` est la source de vérité, versionnée ici avec le code. Les équipes Client et Manager n'ont **pas besoin du fichier en local** : elles consultent la documentation générée automatiquement (Swagger UI) une fois le backend lancé — `http://localhost:8080/api/v1/swagger-ui.html`. Toute modification du contrat passe par une revue du lead Backend avant merge.

## Conventions partagées entre les 3 équipes (identiques dans les 3 dépôts)

1. **Contrat d'API d'abord** — voir ci-dessus.
2. **Authentification par rôle** — un JWT porte le rôle du staff (`CUISINE`, `GERANT`, `MANAGER`, `LIVREUR`) ; l'App Client n'authentifie jamais ses utilisateurs.
3. **Versioning des routes** — toutes les routes sont servies sous `/api/v1`.
4. **Format d'erreur standard** — toute erreur API renvoie `{ code, message, field }`.
5. **Stratégie de branches** — voir la section "CI/CD et stratégie de branches" plus bas.

## CI/CD et stratégie de branches

Flux : `feature/*` → PR vers `develop` → `staging` → `preprod` → `main`. Structure identique dans les 3 dépôts (`Backend_RestoApp`, `Client_RestoApp`, `Manager_RestoApp`).

- **Branche par défaut du dépôt : `develop`** (pas `main`) — pour que GitHub propose `develop` par défaut à la création d'une PR, et éviter les merges accidentels vers `main` (incident déjà vécu sur l'ancien monorepo).
- **`develop`** : la CI (check `build`, voir `.github/workflows/`) doit passer avant de merger une PR. Pas de relecture obligatoire.
- **`staging`, `preprod`, `main`** : la CI (check `build`) doit passer **et** au moins 1 relecture (review) approuvée est obligatoire avant de merger.
- Ces règles sont appliquées via les "branch protection rules" GitHub sur les 3 dépôts (branches principales protégées, non contournables sauf par un admin).
- L'ancien monorepo `Resto_app` est désormais **archivé** (lecture seule) — ce dépôt-ci est la référence actuelle pour l'équipe Backend.

## Palette / identité

- Rouge : `#A6192E` · Noir : `#141414` · Blanc : `#FFFFFF`
