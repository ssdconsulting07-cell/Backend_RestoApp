-- Feedbacks de novembre 2026 (App Client et Manager).
-- Regle Flyway du depot : on AJOUTE seulement (aucune colonne supprimee) ;
-- l'ancienne colonne plats_du_jour.jour reste ecrite (premier jour choisi)
-- et ne sera retiree que dans une version suivante.

-- 1. Plats du jour : service(s) Dejeuner / Diner et plusieurs jours par plat.
ALTER TABLE `plats_du_jour`
  ADD COLUMN `dejeuner` bit(1) NOT NULL DEFAULT b'1',
  ADD COLUMN `diner` bit(1) NOT NULL DEFAULT b'1';

CREATE TABLE `plats_du_jour_jours` (
  `plat_id` bigint NOT NULL,
  `jour` varchar(10) NOT NULL,
  PRIMARY KEY (`plat_id`, `jour`),
  KEY `ix_plats_du_jour_jours_jour` (`jour`),
  CONSTRAINT `fk_plats_du_jour_jours_plat` FOREIGN KEY (`plat_id`) REFERENCES `plats_du_jour` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_plats_du_jour_jours_jour` CHECK (`jour` IN ('LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI', 'DIMANCHE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `plats_du_jour_jours` (`plat_id`, `jour`) SELECT `id`, `jour` FROM `plats_du_jour`;

-- 2. Horaires des services, reglables depuis le Manager. Le diner peut
--    finir apres minuit (fin < debut) : il appartient alors au jour ou il a
--    commence.
CREATE TABLE `horaires_services` (
  `service` varchar(10) NOT NULL,
  `debut` time NOT NULL,
  `fin` time NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`service`),
  CONSTRAINT `ck_horaires_services_service` CHECK (`service` IN ('DEJEUNER', 'DINER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `horaires_services` (`service`, `debut`, `fin`, `modifie_le`) VALUES
  ('DEJEUNER', '11:00:00', '15:00:00', UTC_TIMESTAMP(6)),
  ('DINER', '18:00:00', '01:00:00', UTC_TIMESTAMP(6));

-- 3. Tendance : automatique (les plus commandes), forcable par le Gerant.
--    tendance_manuelle NULL = automatique ; la colonne tendance garde la
--    valeur effective (lue par les versions precedentes).
ALTER TABLE `produits`
  ADD COLUMN `tendance_manuelle` bit(1) DEFAULT NULL,
  ADD COLUMN `tendance_auto` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `ventes_recentes` int NOT NULL DEFAULT 0;

-- Les badges poses a la main avant cette version restent forces.
UPDATE `produits` SET `tendance_manuelle` = b'1' WHERE `tendance` = b'1';

-- 4. Plusieurs accompagnements par ligne de commande (nom et prix figes).
CREATE TABLE `lignes_commande_accompagnements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ligne_id` bigint NOT NULL,
  `position` int NOT NULL,
  `accompagnement_id` bigint NOT NULL,
  `nom` varchar(80) NOT NULL,
  `prix` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_lignes_commande_accompagnements` (`ligne_id`, `position`),
  CONSTRAINT `fk_lignes_commande_accompagnements` FOREIGN KEY (`ligne_id`) REFERENCES `lignes_commande` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_lignes_commande_accompagnements_prix` CHECK (`prix` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Reprise des commandes existantes (un accompagnement au plus).
INSERT INTO `lignes_commande_accompagnements` (`ligne_id`, `position`, `accompagnement_id`, `nom`, `prix`)
SELECT `id`, 0, `accompagnement_id`, `accompagnement_nom`, COALESCE(`accompagnement_prix`, 0)
FROM `lignes_commande`
WHERE `accompagnement_id` IS NOT NULL AND `accompagnement_nom` IS NOT NULL;

-- 5. Livreur attribue a une commande en livraison (coordonnees figees au
--    depart, affichees au client).
ALTER TABLE `commandes`
  ADD COLUMN `livreur_id` bigint DEFAULT NULL,
  ADD COLUMN `livreur_identifiant` varchar(60) DEFAULT NULL,
  ADD COLUMN `livreur_nom` varchar(160) DEFAULT NULL,
  ADD COLUMN `livreur_telephone` varchar(30) DEFAULT NULL;
