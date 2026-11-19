# Démonstration : un restaurant fictif en une commande

**Avant** : SenYummies, la marque par défaut, sans aucune configuration (données de démonstration du profil dev).
**Après** : Kër Saveurs, le restaurant fictif de `clients/exemple/`, généré avec :

```sh
npm run client -- exemple
cd deploiements/exemple
docker compose --env-file .env --env-file .env.secrets -f docker-compose.yml -f docker-compose.local.yml up -d
```

Les **mêmes images Docker** servent les deux versions : seuls `.env`, `marque/` et `donnees/` changent. Au premier démarrage, le backend a importé 41 fiches : 5 catégories, 13 produits, 6 accompagnements et condiments, 3 plats du jour, 1 annonce, la région Thiès et ses 5 zones, les 2 services et 5 comptes. Au redémarrage suivant, il a journalisé « passage … déjà appliqué, rien à faire ».

Captures du 19 novembre 2026 : Client en 390 px de large (mobile), Manager en 1440 px.

## App Client

| | Avant (SenYummies) | Après (Kër Saveurs) |
|---|---|---|
| Écran de démarrage : logo, nom en deux tons, slogan, photo de fond | ![](avant-client-1-splash.jpg) | ![](apres-client-1-splash.jpg) |
| Accueil : en-tête, bannière, pastille, slogan, couleur | ![](avant-client-2-accueil.jpg) | ![](apres-client-2-accueil.jpg) |
| Menu : catalogue importé, horaires des services du restaurant | ![](avant-client-3-menu.jpg) | ![](apres-client-3-menu.jpg) |
| Commande : modes proposés | ![](avant-client-4-livraison.jpg) | ![](apres-client-4-livraison.jpg) |
| Paiement : seulement Wave et Orange Money (`restaurant.json`) | ![](avant-client-5-paiement.jpg) | ![](apres-client-5-paiement.jpg) |
| « Besoin d'aide ? » : WhatsApp, réseaux, adresse, horaires d'ouverture | ![](avant-client-6-aide.jpg) | ![](apres-client-6-aide.jpg) |
| Annonce de lancement importée | ![](avant-client-7-annonce.jpg) | ![](apres-client-7-annonce.jpg) |

## Manager

| | Avant | Après |
|---|---|---|
| Connexion : nom, couleur, photos du restaurant | ![](avant-manager-1-connexion.jpg) | ![](apres-manager-1-connexion.jpg) |
| Tableau de bord du Gérant (compte de `comptes-initiaux.txt`, mot de passe changé à la première connexion) | ![](avant-manager-2-gerant.jpg) | ![](apres-manager-2-gerant.jpg) |
| Produits : catalogue et photos importés | ![](avant-manager-3-produits.jpg) | ![](apres-manager-3-produits.jpg) |
| Espace Vendeur | ![](avant-manager-4-caisse.jpg) | ![](apres-manager-4-caisse.jpg) |
