# Écarts entre l'API implémentée et `openapi.yaml` (v0.3.0)

> **Statut : à valider par le lead Backend.** `openapi.yaml` n'est **pas** modifié tant que ces écarts ne sont pas validés. La documentation générée depuis le code (`/api/v1/swagger-ui.html`, profil dev) reflète l'API réelle.
>
> Le document est complété à chaque lot. Pour chaque écart : ce qui change, pourquoi, et l'impact sur les fronts.

## Lot 2 : catalogue, annonces, zones, photos

### Compatibles avec les fronts actuels (ajouts ou précisions)

| # | Écart | Pourquoi | Impact fronts |
|---|---|---|---|
| 2.1 | **Montants en entiers** (`prix`, `prixLivraison` : `integer`, F CFA). Un montant décimal est refusé (400), jamais tronqué. | Les montants ne doivent jamais passer par des nombres à virgule. | Aucun : les fronts envoient déjà des entiers. |
| 2.2 | **`Categorie.accompagnementPropose`** (booléen, nouveau) | Aujourd'hui, les fronts décident qu'un produit propose un accompagnement si sa catégorie **s'appelle « Burgers »** : renommer la catégorie casserait les commandes. Le lot 3 s'en sert pour recalculer les prix. | Lot 4 : lire ce champ au lieu de comparer le nom. |
| 2.3 | **`version`** sur toutes les ressources modifiables (catégorie, produit, plat du jour, accompagnement, annonce, région, secteur). Les requêtes de modification l'acceptent ; s'il ne correspond plus à la fiche, la réponse est **409 `VERSION_CONFLICT`**. | Deux Gérants modifient la même fiche : le second est prévenu au lieu d'écraser le travail du premier. | Lot 4 : renvoyer la `version` lue. Champ **facultatif** en attendant (sans lui, pas de contrôle). |
| 2.4 | **Routes d'écriture (rôle GERANT)** : `POST` / `PUT /{id}` / `PATCH /{id}/statut` pour `categories`, `produits`, `plats-du-jour`, `accompagnements-condiments` et `annonces` ; `PATCH /regions/{code}`, `POST /regions/{code}/secteurs`, `PUT /secteurs/{id}`, `PATCH /secteurs/{id}/masque`, `DELETE /secteurs/{id}` | Le contrat ne décrivait que les lectures. Création en `BROUILLON` ; publication par `PATCH .../statut` ; **pas de suppression du catalogue** (on archive). | Lot 4 : remplacer les contextes en mémoire du Manager par ces appels. |
| 2.5 | **Cycle de publication** : `BROUILLON → ACTIF`, `ACTIF → BROUILLON` (dépublier), `BROUILLON/ACTIF → ARCHIVE`, `ARCHIVE → BROUILLON` (restaurer). `ARCHIVE → ACTIF` direct : 409 `TRANSITION_INTERDITE`. | Mêmes actions que les écrans du Manager. | Aucun. |
| 2.6 | **Archiver une catégorie non vide** : 409 `CATEGORIE_NON_VIDE`, sauf si le corps contient `reassignerVers` (catégorie qui reçoit les produits). Publier un produit d'une catégorie archivée : 409 `CATEGORIE_ARCHIVEE`. | Même règle que la fenêtre « Réaffecter » du Manager. | Lot 4 : envoyer `reassignerVers`. |
| 2.7 | **Photos** : `POST /photos` (GERANT, multipart, champ `fichier`) renvoie `{url, largeur, hauteur, octets}` ; `GET /photos/{nom}` est public, avec un cache d'un an. JPEG, PNG ou WebP, 2 Mo max (413 au-delà), type vérifié sur le contenu, redimensionné à 1600 px, **sans métadonnées EXIF**. | Absent du contrat. | Lot 4 : le Manager dépose la photo recadrée, puis met l'`url` renvoyée dans `photoUrl`. |
| 2.8 | **`photoUrl` / `imageUrl`** : seulement une adresse `https://` ou une photo déposée (`/api/v1/photos/...`), 500 caractères max ; sinon 400 sur le champ. Une image absente vaut `null`, pas `""`. | Empêche `javascript:`, `data:` et `http:` en clair. | Aucun : les données actuelles sont en https. |
| 2.9 | **Annonce `publishedAt`** : renouvelé à chaque (re)publication. | L'App Client s'en sert pour savoir si l'annonce a déjà été vue : republier la fait réapparaître. | Aucun. |
| 2.10 | **Secteurs** : `GET /regions/{code}/secteurs` renvoie, sans droits Gérant, seulement les zones **non masquées** d'une région **ouverte** (une liste vide sinon). Le Gérant voit tout. Région inconnue : 404. | Ne jamais proposer une zone fermée au client. | Aucun. |
| 2.11 | **Champs ajoutés** : `version` partout ; `updatedAt` sur `Secteur` ; `version` sur `Region`. | Voir 2.3. | Aucun. |
| 2.12 | **Cache HTTP** : les lectures publiques renvoient `Cache-Control: public, max-age=30` ; le Gérant reçoit `no-store`. | Soulage le serveur aux heures de pointe. | Aucun. |

### À trancher (comportement différent du contrat)

| # | Écart | Choix fait | Pourquoi |
|---|---|---|---|
| 2.13 | **`?statut=` autre que `ACTIF` sans droits Gérant** : le contrat dit seulement « filtre facultatif ». | **403** (et sans filtre, seul le contenu `ACTIF` est renvoyé). | Un brouillon ne doit jamais fuiter par un simple paramètre d'URL. |
| 2.14 | **Format des identifiants** : texte (conforme), mais numérique (`"12"`) au lieu de `"produit-12"` comme dans les données de démo des fronts. | Identifiants numériques en texte. | Les identifiants sont générés par la base. Les fronts les traitent déjà comme opaques. Un panier enregistré sur un téléphone avec d'anciens identifiants sera simplement marqué « plus disponible » (comportement déjà prévu par l'App Client). |
| 2.15 | **Produit visible côté client** : `ACTIF` **et** dans une catégorie `ACTIF` (le contrat ne parle que du statut du produit). | Filtre côté serveur. | Un produit publié dans une catégorie en brouillon n'a pas à apparaître. |

### Codes d'erreur introduits (`ApiError.code`)

`VERSION_CONFLICT`, `TRANSITION_INTERDITE`, `CATEGORIE_NON_VIDE`, `CATEGORIE_ARCHIVEE`, `CATEGORIE_INCONNUE`, `CATEGORIE_INVALIDE`, `NOM_DEJA_UTILISE`, `SECTEUR_OFFICIEL`, `SECTEUR_EXISTANT`, `FORMAT_IMAGE_REFUSE`, `IMAGE_ILLISIBLE`, `IMAGE_TROP_GRANDE`, `FICHIER_VIDE`, `FICHIER_TROP_VOLUMINEUX`, `PAYLOAD_TOO_LARGE`, `IDENTIFIANT_INVALIDE`, `INVALID_BODY`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONFLIT_DONNEES`.
