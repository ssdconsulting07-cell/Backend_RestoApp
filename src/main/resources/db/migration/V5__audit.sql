-- V5 (lot 5) : journal d'audit.
--
-- Chaque action d'ecriture du staff (et du systeme : paiements, expirations)
-- y est consignee DANS la transaction de l'action : pas d'action sans trace,
-- pas de trace d'une action annulee. Lecture seule (aucune route de
-- modification ou de suppression). Jamais de donnee personnelle de client
-- dans `message`.

CREATE TABLE `audit_evenements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `horodatage` datetime(6) NOT NULL,
  -- Identifiant du membre du staff, ou "client", "systeme", "prestataire".
  `acteur` varchar(255) NOT NULL,
  -- Nom affiche (prenom nom) fige au moment de l'action.
  `acteur_nom` varchar(130) NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  `domaine` varchar(20) NOT NULL,
  `action` varchar(40) NOT NULL,
  `cible_id` varchar(100) DEFAULT NULL,
  -- Phrase lisible, telle qu'affichee dans le Journal d'audit du Manager.
  `message` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_audit_horodatage` (`horodatage`),
  KEY `ix_audit_role_horodatage` (`role`, `horodatage`),
  KEY `ix_audit_domaine_horodatage` (`domaine`, `horodatage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
