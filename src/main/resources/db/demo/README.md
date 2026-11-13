# Donnees de demonstration (profil dev uniquement)

Emplacement Flyway charge **seulement** avec le profil `dev`
(`application-dev.properties` : `spring.flyway.locations`). Jamais en prod.

- Migrations **repetables** `R__demo_<sujet>.sql` : rejouees quand leur contenu
  change, appliquees apres les migrations versionnees. Elles n'occupent donc
  aucun numero de version (pas de trou ni de conflit avec `db/migration`).
- Ecrites pour etre rejouees sans doublon (`INSERT ... ON DUPLICATE KEY UPDATE`).
- Contenu repris des donnees de demo du Manager (`src/features/gerant/*Data.js`,
  `senegalZones.js`), coherent avec le `db.json` de l'App Client.

Les comptes du personnel de dev restent crees par `StaffUserSeed`
(mots de passe fournis par les variables `APP_AUTH_SEED_*`, jamais en SQL).

## Fichiers (generes, ne pas modifier a la main)

| Fichier | Contenu |
|---|---|
| `R__demo_catalogue.sql` | 6 categories, 27 produits, 9 plats du jour, 11 accompagnements et condiments |
| `R__demo_annonces.sql` | 5 annonces (chaines de slides) |
| `R__demo_zones.sql` | 14 regions, 125 arrondissements officiels (Dakar ouverte, prix de demo) |

Regeneration, quand les donnees de demo du Manager changent :

    node scripts/demo/generer-donnees-demo.mjs [chemin/vers/Manager_RestoApp]

Attention : une donnee de demo modifiee dans le Manager en dev sera
ecrasee au prochain changement de ces fichiers (c'est leur role).
