# Mise en production

Tout est prêt sauf ce qui dépend des décisions du client (voir [`docs/reste-a-faire.md`](../../docs/reste-a-faire.md)) :

| Décision | Ce qu'elle débloque ici |
|---|---|
| Hébergement | le serveur et les deux domaines |
| Prestataire de paiement | `PAIEMENT_PRESTATAIRE` et ses clés (le backend refuse de démarrer en prod sans prestataire réel) |
| Vrai menu | saisi par le Gérant dans le Manager après le premier lancement |

## Ce que contient ce dossier

- **Caddy** : HTTPS automatique (Let's Encrypt, renouvellement compris), HSTS. Seul service exposé (ports 80 et 443).
- **MySQL 8** : jamais exposé à Internet.
- **Sauvegarde** : chaque nuit vers 02:00 (heure de Dakar), `mysqldump` compressé, conservé 14 jours.
- **Backend** : profil `prod`. Le serveur refuse de démarrer si un réglage est dangereux : secret faible, comptes de démo, paiement simulé, CORS `*`.
- **App Client et Manager** : nginx sans privilèges, en-têtes de sécurité (CSP…).
- Journaux en rotation (10 Mo × 5 par service).

## Premier lancement

1. **Serveur** : un VPS avec Docker. Ouvrir les ports 80 et 443, et seulement eux (plus SSH).
2. **DNS** : deux enregistrements A/AAAA vers le serveur, par exemple `commande.<domaine>` et `gestion.<domaine>`.
3. **Fichiers** : cloner les trois dépôts côte à côte, puis :

   ```bash
   cd Backend_RestoApp/deploiement/prod
   cp .env.exemple .env && chmod 600 .env
   ```

   Renseigner toutes les valeurs, **y compris** `MANAGER_INITIAL_IDENTIFIANT` et `MANAGER_INITIAL_MOT_DE_PASSE`.
4. **Démarrer** :

   ```bash
   docker compose up -d --build
   docker compose logs -f backend   # attendre "Started", corriger sinon
   ```

5. **Premier compte** : se connecter au Manager avec ce compte et changer le mot de passe. Ensuite :
   - **vider** les deux lignes `MANAGER_INITIAL_*` du `.env` ;
   - relancer `docker compose up -d` ;
   - créer les autres comptes du personnel depuis le Manager.
6. **Zones de livraison** : les 14 régions et leurs arrondissements officiels sont déjà en base, **toutes fermées**. Le Gérant vérifie les prix de livraison, puis ouvre les régions livrées.
7. **Menu** : saisi par le Gérant (catégories, produits, photos, plats du jour, annonces).

## Vérifications de mise en ligne

- [ ] `https://<domaine-client>` et `https://<domaine-manager>` s'ouvrent, avec un certificat valide.
- [ ] `https://<domaine-client>/api/v1/health` répond `{"status":"UP"}`.
- [ ] `https://<domaine-client>/api/v1/swagger-ui.html` répond **404** (Swagger coupé en prod).
- [ ] Une commande avec les **clés de test** du prestataire passe, et arrive en direct en Cuisine.
- [ ] La géolocalisation fonctionne sur un téléphone en 4G.
- [ ] `docker compose logs sauvegarde` affiche « sauvegarde OK » le lendemain.

## Sauvegardes

- **Lister** :

  ```bash
  docker compose run --rm --entrypoint ls sauvegarde -l /sauvegardes
  ```

- **Copier hors du serveur** (à automatiser, par exemple vers un stockage objet ou un autre serveur) :

  ```bash
  docker compose cp sauvegarde:/sauvegardes ./copie-sauvegardes
  ```

- **Restaurer**, à tester au moins une fois **avant** d'en avoir besoin :

  ```bash
  gunzip -c senyummies-AAAAMMJJ-HHMM.sql.gz | docker compose exec -T mysql \
    sh -c 'mysql -u senyummies -p"$MYSQL_PASSWORD" senyummies'
  ```

- **Photos** : le volume `photos` est à sauvegarder aussi, ou à remplacer par un stockage objet (voir « Reste à faire »).

## Mettre à jour

```bash
git -C ../../../Client_RestoApp pull && git -C ../../../Manager_RestoApp pull && git pull
docker compose up -d --build
```

Les migrations Flyway s'appliquent au démarrage du backend. Elles ne font que des ajouts, donc un retour à la version précédente reste possible.
