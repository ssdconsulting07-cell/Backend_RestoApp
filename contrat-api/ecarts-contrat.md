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

## Lot 3 : commandes

### Compatibles avec l'App Client actuelle (verifie : parcours complet de l'App Client contre le vrai backend)

| # | Ecart | Pourquoi | Impact fronts |
|---|---|---|---|
| 3.1 | **Montants entiers** (`prixUnitaire`, `sousTotal`, `fraisLivraison`, `total`, `montantRecu`, `monnaieRendue`). | Voir 2.1. | Aucun. |
| 3.2 | **`LigneCommande.accompagnementId`** (nouveau). En attendant, l'accompagnement est retrouve **par son nom** parmi les accompagnements publies. | Le serveur doit pouvoir verifier le prix de l'accompagnement. | Lot 4 : envoyer `accompagnementId` (le panier du Client l'a deja). |
| 3.3 | **Commande APP** : creee en `CREEE` / `en_attente`, puis **payee sur confirmation du serveur** (simulateur : immediat, donc la reponse est deja `PAYEE`). Le contrat disait « paiement avant creation », incompatible avec `/paiements/init` qui attend un `commandeId`. `statut` et `statutPaiement` envoyes par le client sont **ignores**. | Seul le serveur constate un paiement. | Aucun en mode simule. Avec un vrai prestataire : suivre `paiement.redirectUrl` (lot 4). |
| 3.4 | **Reponse de creation** : la commande + `jetonSuivi` (secret, renvoye **une seule fois**) + `paiement {redirectUrl, simule}`. | Suivi securise (3.5). | Lot 4 : conserver le jeton de chaque commande sur l'appareil. |
| 3.5 | **Suivi client** : `GET /commandes/{id}` sans en-tete **`X-Jeton-Suivi`** (ou jeton faux) -> **404**. `GET /commandes?clientTelephone=` -> **vue reduite** : ni nom, ni position GPS, ni note (le numero fourni est renvoye). | Les numeros de commande sont sequentiels et un numero de telephone n'est pas un secret : sans jeton, n'importe qui lirait les commandes (et la position) des autres. | Lot 4 : envoyer `X-Jeton-Suivi` pour le detail complet. |
| 3.6 | **En-tete `Idempotency-Key`** (facultatif pour l'instant) : meme cle + meme corps -> premiere reponse rejouee (`Idempotency-Replayed: true`) ; meme cle + autre corps -> 409 `IDEMPOTENCE_CONFLIT` ; envoi simultane en cours -> 409 `REQUETE_EN_COURS`. | Une commande renvoyee apres une coupure n'est creee (et payee) qu'une fois. | Lot 4 : une cle (UUID) par tentative de commande, reutilisee lors d'un renvoi. Puis l'en-tete deviendra obligatoire. |
| 3.7 | **Valeurs du client seulement controlees** : `prixUnitaire`, `accompagnementPrix`, `sousTotal`, `fraisLivraison`, `total` sont facultatifs ; s'ils sont envoyes et differents du calcul -> 400 `PRIX_INCOHERENT` / `TOTAL_INCOHERENT` avec le champ (`lignes[0].prixUnitaire`...). `adresseLivraison` et `regionLivraison` sont fixees par le serveur depuis `secteurId`. | Aucun prix venant du client n'est jamais repris. | Aucun. |
| 3.8 | **Vente comptoir** (`canal: COMPTOIR`, role VENDEUR, mode RETRAIT) : `montantRecu` (especes) -> `monnaieRendue` calculee par le serveur ; statut `PAYEE` directement. Pas d'entite « Vente » separee. | Une vente comptoir est une commande. | Lot 4 : la caisse du Manager appelle `POST /commandes`. |
| 3.9 | **Champs ajoutes a `Commande`** : `historique [{statut, le, par, role}]` (`par`/`role` pour le staff seulement), `version`, `montantRecu`, `monnaieRendue`, `remboursementASignaler` (commande payee puis annulee). | Tracabilite et compensation. | Aucun. |
| 3.10 | **`GET /commandes` staff** : chaque role ne voit que sa file (Cuisine : commandes payees ; Livreur : livraisons PRETE / EN_LIVRAISON / LIVREE ; Vendeur : commandes a emporter ; Manager / Gerant : tout). Parametres `statut`, `canal`, `depuis` (48 h par defaut, 31 jours maximum). Donnees personnelles par role : telephone pour Cuisine, Livreur et Vendeur ; position GPS pour le Livreur seulement. | Moindre privilege. | Aucun. |
| 3.11 | **`PATCH /commandes/{id}/statut`** : `version` facultative (409 `VERSION_CONFLICT`) ; deja applique par un autre poste -> 409 `STATUT_DEJA_APPLIQUE` ; hors table -> 403 `TRANSITION_INTERDITE` ; commande hors de la file du role -> 404. | Concurrence Cuisine / Livreur. | Lot 4 : traiter le 409 (« deja pris en charge »). |
| 3.12 | **Temps reel** : `GET /temps-reel/commandes` (SSE, staff), evenement `commande` `{type: COMMANDE_PAYEE \| STATUT_CHANGE, commande}` filtre par role, `id` = numero d'evenement (reprise via `Last-Event-ID`). | Absent du contrat. | Lot 4 : abonnement du Manager (fetch en streaming + bloc nginx). |
| 3.13 | **429 `TROP_DE_REQUETES`** + `Retry-After` sur `POST /commandes` (10/min/IP), suivi sans jeton (30/min/IP), `POST /auth/login` (10/min/IP). | Abus, enumeration de numeros, essais de mots de passe. | Afficher le message ; reessayer apres le delai. |

### Codes d'erreur introduits

`PRIX_INCOHERENT`, `TOTAL_INCOHERENT`, `PRODUIT_INDISPONIBLE`, `PLAT_INDISPONIBLE`, `ACCOMPAGNEMENT_NON_PROPOSE`, `ZONE_REQUISE`, `ZONE_INDISPONIBLE`, `PRENOM_REQUIS`, `TELEPHONE_INVALIDE`, `TELEPHONE_REQUIS`, `MOYEN_PAIEMENT_INVALIDE`, `MOYEN_PAIEMENT_REQUIS`, `MODE_INVALIDE`, `MONTANT_INSUFFISANT`, `IDEMPOTENCE_CONFLIT`, `IDEMPOTENCE_CLE_INVALIDE`, `REQUETE_EN_COURS`, `STATUT_DEJA_APPLIQUE`, `TRANSITION_INTERDITE`, `TROP_DE_REQUETES`.

## Lot paiement : relance, webhook, expiration

| # | Ecart | Pourquoi | Impact fronts |
|---|---|---|---|
| P.1 | **`POST /paiements/init`** : corps `{commandeId}` + en-tete **`X-Jeton-Suivi` obligatoire** (404 sinon). Reponse `{paymentReference, redirectUrl, statutPaiement, statut, simule}`. 409 `PAIEMENT_DEJA_REGLE` si deja payee, 409 `COMMANDE_ANNULEE` si annulee. | Le contrat ne protegeait pas cette route : n'importe qui pouvait lancer un paiement sur la commande d'un autre. | Lot 4 : bouton « Reprendre le paiement » sur une commande en attente. |
| P.2 | **`POST /paiements/webhook`** : notification **authentifiee** (signature du prestataire ; 401 `SIGNATURE_INVALIDE` sinon), **traitee une seule fois** par identifiant d'evenement, repond 200 `{resultat: TRAITEE \| DEJA_TRAITEE \| REFERENCE_INCONNUE \| MONTANT_INCOHERENT}`. Un montant different du total de la commande ne la confirme jamais. | Format du contrat « a preciser » : il depend du prestataire. Le simulateur fixe un format de reference (HMAC-SHA256 du corps + horodatage). | Aucun (appele par le prestataire). |
| P.3 | **Expiration** : une commande APP non payee au bout de **30 min** (`APP_PAIEMENT_DELAI_EXPIRATION`) est annulee (`ANNULEE` / `echoue`). Un paiement confirme ensuite la laisse annulee, passe `statutPaiement` a `paye` et leve **`remboursementASignaler`**. | Compensation : jamais de commande preparee sans paiement, jamais d'argent encaisse sans trace. | Lot 4 : afficher « commande expiree » au client. |
| P.4 | **Simulateur en deux modes** (`APP_PAIEMENT_SIMULE_MODE`) : `immediat` (defaut, App Client actuelle) ou `webhook` (comportement d'un vrai prestataire). | Tester le parcours asynchrone avant le choix du prestataire. | Aucun. |

### Codes d'erreur introduits

`SIGNATURE_INVALIDE`, `PAIEMENT_DEJA_REGLE`, `COMMANDE_ANNULEE`.

## Lot 5 : journal d'audit et statistiques

| # | Ecart | Pourquoi | Impact fronts |
|---|---|---|---|
| 5.1 | **`GET /audit`** (MANAGER) : `?role=&domaine=&du=&au=&page=&taille=` (7 derniers jours par defaut, 92 jours maximum, 200 par page). Reponse `{entrees: [{id, createdAt, acteur {identifiant, nom, role}, domaine, code, cibleId, action}], total, page, taille}`. `action` = la phrase affichee par le Manager. | Absent du contrat. Forme calquee sur `auditData.js` du Manager. | Lot 4 : remplacer `auditData.js` par cette route. |
| 5.2 | **`GET /audit/export.csv`** (MANAGER) : memes filtres ; CSV UTF-8 avec BOM, separateur `;`, colonnes *Date ; Employe ; Identifiant ; Role ; Type ; Description* ; cellules commencant par `= + - @` neutralisees (injection de formules Excel). L'export est lui-meme trace. | Absent du contrat. | Lot 4 : le bouton d'export du Manager peut appeler cette route. |
| 5.3 | **Journal ecrit dans la transaction de l'action** (une action refusee ne laisse aucune trace). Acteurs non humains : `client`, `systeme`, `prestataire`. Desormais traces aussi : creation, modification, desactivation, suppression des comptes du personnel, mots de passe temporaires. Jamais de CNI, telephone, mot de passe ni donnee personnelle de client. | Tracabilite complete. | Aucun. |
| 5.4 | **`GET /statistiques/tableau-de-bord`** (MANAGER, GERANT) : `chiffreAffaires {jour, semaine, mois, septDerniersJours}`, `commandesDuJour {total, encaissees, parStatut, panierMoyen}`, `parCanalDuJour`. | Memes chiffres que `ManagerDashboard.jsx` (`financeData.js`, `commandesData.js`). | Lot 4 : brancher le tableau de bord. |
| 5.5 | **`GET /statistiques/chiffre-affaires`**, **`/top-produits`**, **`/commandes-par-statut`** (MANAGER, GERANT), **`/ventes-comptoir`** (MANAGER, GERANT, et VENDEUR pour ses propres ventes). Periode `du`/`au` (7 jours par defaut, un an maximum). | Absent du contrat. | Lot 4 : historique des ventes du Vendeur. |
| 5.6 | **Definition du chiffre d'affaires** : commandes **payees et non annulees** (une commande payee puis annulee = remboursement, pas du CA). | Meme regle que `commandesEncaissees()` du Manager. | Aucun. |

### Correction d'un bug existant

`POST /staff-users` refusait **toute** creation de compte des qu'un compte MANAGER existait (`role == MANAGER || existsByRole(MANAGER)`) : comme le compte `manager` existe toujours, le Gerant ne pouvait creer aucun compte. Seule la creation d'un **MANAGER** est desormais refusee (compte unique, gere par le systeme).
