-- Import des donnees initiales d'un restaurant (outil-client : npm run client).
--
-- Le backend lit au demarrage le dossier genere par l'outil (import.json +
-- photos) et cree le catalogue, les zones, les horaires, les annonces et les
-- comptes du personnel. L'import est IDEMPOTENT :
--   * import_restaurant_passages : un passage (jeton) n'est applique qu'une
--     fois, meme si le conteneur redemarre cent fois ;
--   * import_restaurant_fiches   : chaque fiche importee (entite + cle stable
--     du fichier) est memorisee avec l'identifiant cree. Une cle deja connue
--     n'est jamais recreee ni ecrasee (le Gerant a pu la modifier ou la
--     supprimer), sauf passage "forcer" explicite (npm run client -- --forcer).
-- Aucune table metier n'est modifiee : rien a changer cote Hibernate.

CREATE TABLE `import_restaurant_passages` (
  `jeton` varchar(80) NOT NULL,
  `restaurant` varchar(80) NOT NULL,
  `forcer` bit(1) NOT NULL DEFAULT b'0',
  `bilan` varchar(500) DEFAULT NULL,
  `applique_le` datetime(6) NOT NULL,
  PRIMARY KEY (`jeton`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `import_restaurant_fiches` (
  `entite` varchar(30) NOT NULL,
  `cle` varchar(120) NOT NULL,
  `ressource_id` varchar(80) NOT NULL,
  `importe_le` datetime(6) NOT NULL,
  PRIMARY KEY (`entite`, `cle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
