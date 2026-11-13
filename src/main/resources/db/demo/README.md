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
Le catalogue, les zones et les annonces de demo arrivent avec le lot 2.
