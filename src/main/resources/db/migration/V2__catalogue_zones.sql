-- V2 (lot 2) : catalogue, annonces et zones de livraison.
--
-- Conventions :
-- - montants en F CFA entiers (BIGINT), jamais de flottant ;
-- - enums stockes en VARCHAR (jamais d'ENUM natif MySQL, fige a la creation) ;
-- - colonne `version` = verrouillage optimiste (deux Gerants qui modifient la
--   meme fiche : le second recoit 409 au lieu d'ecraser) ;
-- - statut de publication : BROUILLON -> ACTIF -> ARCHIVE (seul ACTIF est
--   visible cote client).

CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nom` varchar(80) NOT NULL,
  `statut` varchar(12) NOT NULL,
  -- Les produits de cette categorie proposent un accompagnement facture en
  -- plus (aujourd'hui : Burgers). Remplace la regle "categorie nommee
  -- Burgers" des fronts.
  `accompagnement_propose` bit(1) NOT NULL DEFAULT b'0',
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_nom` (`nom`),
  CONSTRAINT `ck_categories_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `produits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nom` varchar(80) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `prix` bigint NOT NULL,
  `categorie_id` bigint NOT NULL,
  `photo_url` varchar(500) DEFAULT NULL,
  `statut` varchar(12) NOT NULL,
  `disponibilite` varchar(10) NOT NULL,
  `tendance` bit(1) NOT NULL DEFAULT b'0',
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_produits_statut` (`statut`),
  KEY `ix_produits_categorie` (`categorie_id`),
  CONSTRAINT `fk_produits_categorie` FOREIGN KEY (`categorie_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `ck_produits_prix` CHECK (`prix` > 0),
  CONSTRAINT `ck_produits_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE')),
  CONSTRAINT `ck_produits_disponibilite` CHECK (`disponibilite` IN ('EN_STOCK', 'RUPTURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plats_du_jour` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nom` varchar(80) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `prix` bigint NOT NULL,
  `photo_url` varchar(500) DEFAULT NULL,
  `jour` varchar(10) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_plats_du_jour_jour_statut` (`jour`, `statut`),
  CONSTRAINT `ck_plats_du_jour_prix` CHECK (`prix` > 0),
  CONSTRAINT `ck_plats_du_jour_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE')),
  CONSTRAINT `ck_plats_du_jour_jour` CHECK (`jour` IN ('LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI', 'DIMANCHE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `accompagnements_condiments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nom` varchar(80) NOT NULL,
  `prix` bigint NOT NULL,
  `type` varchar(15) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_accomp_prix` CHECK (`prix` >= 0),
  CONSTRAINT `ck_accomp_type` CHECK (`type` IN ('ACCOMPAGNEMENT', 'CONDIMENT')),
  CONSTRAINT `ck_accomp_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `annonces` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `titre` varchar(80) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `publiee_le` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_annonces_statut` CHECK (`statut` IN ('BROUILLON', 'ACTIF', 'ARCHIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `annonce_slides` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `annonce_id` bigint NOT NULL,
  `position` int NOT NULL,
  `titre` varchar(80) NOT NULL,
  `message` varchar(300) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_annonce_slides_annonce` (`annonce_id`, `position`),
  CONSTRAINT `fk_annonce_slides_annonce` FOREIGN KEY (`annonce_id`) REFERENCES `annonces` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `regions` (
  `code` varchar(20) NOT NULL,
  `nom` varchar(60) NOT NULL,
  -- Livraison ouverte dans cette region (decision du Gerant).
  `active` bit(1) NOT NULL DEFAULT b'0',
  `version` bigint NOT NULL DEFAULT 0,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `secteurs` (
  -- Identifiant lisible et stable, ex. DAKAR-ALMADIES (le contrat expose des
  -- identifiants de secteur en texte).
  `id` varchar(80) NOT NULL,
  `region_code` varchar(20) NOT NULL,
  `departement` varchar(60) NOT NULL,
  `nom` varchar(80) NOT NULL,
  `prix_livraison` bigint NOT NULL,
  -- Centre approximatif (arrondissements officiels) : deduction de la zone
  -- depuis la position GPS du client. NULL pour les zones ajoutees a la main.
  `centre_latitude` double DEFAULT NULL,
  `centre_longitude` double DEFAULT NULL,
  -- Arrondissement officiel : peut etre masque, jamais supprime.
  `est_officiel` bit(1) NOT NULL DEFAULT b'0',
  `est_masque` bit(1) NOT NULL DEFAULT b'0',
  `version` bigint NOT NULL DEFAULT 0,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_secteurs_region_nom` (`region_code`, `nom`),
  CONSTRAINT `fk_secteurs_region` FOREIGN KEY (`region_code`) REFERENCES `regions` (`code`),
  CONSTRAINT `ck_secteurs_prix` CHECK (`prix_livraison` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
