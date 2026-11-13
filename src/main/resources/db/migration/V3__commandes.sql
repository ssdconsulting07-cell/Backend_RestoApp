-- V3 (lot 3) : commandes, lignes, historique des statuts, paiements,
-- idempotence et evenements sortants (outbox du temps reel).
--
-- Une commande, ses lignes, son paiement, son historique, son evenement
-- sortant et son entree d'audit sont ecrits dans UNE transaction : rien a
-- moitie fait.

CREATE TABLE `commandes` (
  -- Numero de ticket affiche (Cuisine, Livreur, App Client) et encode dans le
  -- QR de livraison "SENYUMMIES-<id>". Sequentiel, donc devinable : un client
  -- n'accede a une commande qu'avec son jeton de suivi.
  `id` bigint NOT NULL AUTO_INCREMENT,
  `canal` varchar(10) NOT NULL,
  `mode` varchar(10) NOT NULL,
  `statut` varchar(16) NOT NULL,
  `moyen_paiement` varchar(15) NOT NULL,
  `statut_paiement` varchar(12) NOT NULL,
  `client_prenom` varchar(60) DEFAULT NULL,
  `client_nom` varchar(60) DEFAULT NULL,
  `client_telephone` varchar(16) DEFAULT NULL,
  `secteur_id` varchar(80) DEFAULT NULL,
  `adresse_livraison` varchar(80) DEFAULT NULL,
  `region_livraison` varchar(20) DEFAULT NULL,
  `position_latitude` double DEFAULT NULL,
  `position_longitude` double DEFAULT NULL,
  `position_precision` int DEFAULT NULL,
  `note` varchar(300) DEFAULT NULL,
  -- Montants en F CFA entiers, recalcules par le serveur.
  `sous_total` bigint NOT NULL,
  `frais_livraison` bigint NOT NULL,
  `total` bigint NOT NULL,
  -- Vente comptoir en especes : montant recu, monnaie calculee par le serveur.
  `montant_recu` bigint DEFAULT NULL,
  `monnaie_rendue` bigint DEFAULT NULL,
  -- Empreinte SHA-256 du jeton de suivi (le jeton lui-meme n'est jamais stocke).
  `jeton_suivi_empreinte` char(64) DEFAULT NULL,
  -- Commande payee puis annulee : remboursement a traiter (compensation).
  `remboursement_a_signaler` bit(1) NOT NULL DEFAULT b'0',
  `cree_par` varchar(255) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_commandes_statut` (`statut`),
  KEY `ix_commandes_telephone` (`client_telephone`, `cree_le`),
  KEY `ix_commandes_cree_le` (`cree_le`),
  KEY `ix_commandes_mode_statut` (`mode`, `statut`),
  CONSTRAINT `ck_commandes_canal` CHECK (`canal` IN ('APP', 'COMPTOIR')),
  CONSTRAINT `ck_commandes_mode` CHECK (`mode` IN ('LIVRAISON', 'RETRAIT')),
  CONSTRAINT `ck_commandes_statut` CHECK (`statut` IN ('CREEE', 'PAYEE', 'EN_PREPARATION', 'PRETE', 'EN_LIVRAISON', 'LIVREE', 'RETIREE', 'ANNULEE')),
  CONSTRAINT `ck_commandes_paiement` CHECK (`statut_paiement` IN ('en_attente', 'paye', 'echoue')),
  CONSTRAINT `ck_commandes_montants` CHECK (`sous_total` > 0 AND `frais_livraison` >= 0 AND `total` = `sous_total` + `frais_livraison`)
) ENGINE=InnoDB AUTO_INCREMENT=1001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lignes_commande` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commande_id` bigint NOT NULL,
  `position` int NOT NULL,
  `type` varchar(12) NOT NULL,
  `produit_id` bigint NOT NULL,
  -- Nom et prix FIGES a la commande : un changement de prix ulterieur ne
  -- modifie jamais une commande passee.
  `nom` varchar(80) NOT NULL,
  `prix_unitaire` bigint NOT NULL,
  `quantite` int NOT NULL,
  `accompagnement_id` bigint DEFAULT NULL,
  `accompagnement_nom` varchar(80) DEFAULT NULL,
  `accompagnement_prix` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_lignes_commande` (`commande_id`, `position`),
  CONSTRAINT `fk_lignes_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `ck_lignes_type` CHECK (`type` IN ('PRODUIT', 'PLAT_DU_JOUR')),
  CONSTRAINT `ck_lignes_quantite` CHECK (`quantite` BETWEEN 1 AND 50),
  CONSTRAINT `ck_lignes_prix` CHECK (`prix_unitaire` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `historique_statuts_commande` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commande_id` bigint NOT NULL,
  `statut` varchar(16) NOT NULL,
  `horodatage` datetime(6) NOT NULL,
  -- Identifiant du membre du staff, "client" ou "systeme" (paiement).
  `acteur` varchar(255) NOT NULL,
  `role` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_historique_commande` (`commande_id`, `horodatage`),
  CONSTRAINT `fk_historique_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `paiements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commande_id` bigint NOT NULL,
  `prestataire` varchar(30) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `statut` varchar(12) NOT NULL,
  `montant` bigint NOT NULL,
  -- Paiement simule (developpement) : jamais d'argent reel.
  `simule` bit(1) NOT NULL DEFAULT b'0',
  `cree_le` datetime(6) NOT NULL,
  `modifie_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paiements_reference` (`prestataire`, `reference`),
  KEY `ix_paiements_commande` (`commande_id`),
  CONSTRAINT `fk_paiements_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`),
  CONSTRAINT `ck_paiements_statut` CHECK (`statut` IN ('en_attente', 'paye', 'echoue')),
  CONSTRAINT `ck_paiements_montant` CHECK (`montant` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- En-tete Idempotency-Key : une commande renvoyee apres une coupure reseau
-- n'est creee qu'une fois. Conserve 24 h (purge planifiee).
CREATE TABLE `cles_idempotence` (
  `cle` varchar(100) NOT NULL,
  `empreinte_requete` char(64) NOT NULL,
  `commande_id` bigint DEFAULT NULL,
  `reponse` mediumtext DEFAULT NULL,
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`cle`),
  KEY `ix_cles_idempotence_cree_le` (`cree_le`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Outbox : evenement ecrit dans la transaction de la commande, publie
-- ensuite vers le temps reel (SSE). Une commande enregistree produit
-- toujours son evenement, meme si le serveur s'arrete juste apres.
CREATE TABLE `evenements_sortants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(40) NOT NULL,
  `commande_id` bigint NOT NULL,
  `cree_le` datetime(6) NOT NULL,
  `publie_le` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_evenements_a_publier` (`publie_le`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
