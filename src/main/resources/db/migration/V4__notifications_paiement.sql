-- V4 (lot paiement) : notifications (webhooks) recues des prestataires.
--
-- Un prestataire renvoie souvent la meme notification (reessais reseau). La
-- contrainte d'unicite (prestataire, evenement) garantit qu'une notification
-- n'est traitee qu'une fois : la seconde est acquittee sans effet.

CREATE TABLE `notifications_paiement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prestataire` varchar(30) NOT NULL,
  `evenement_id` varchar(100) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `montant` bigint NOT NULL,
  `recue_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notifications_evenement` (`prestataire`, `evenement_id`),
  CONSTRAINT `ck_notifications_statut` CHECK (`statut` IN ('en_attente', 'paye', 'echoue'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Recherche des commandes en attente de paiement depuis trop longtemps.
CREATE INDEX `ix_commandes_statut_cree_le` ON `commandes` (`statut`, `cree_le`);
