-- V7 : avis du client sur sa livraison (note du livreur).
--
-- Un seul avis par commande livree, donne par le client qui a passe la
-- commande (jeton de suivi). `livreur` = identifiant du membre du staff qui
-- a valide la livraison (historique LIVREE), fige au moment de l'avis : la
-- moyenne par livreur s'affiche dans la gestion du personnel.
CREATE TABLE `avis_livraison` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commande_id` bigint NOT NULL,
  `livreur` varchar(255) DEFAULT NULL,
  `note` int NOT NULL,
  `commentaire` varchar(300) DEFAULT NULL,
  `cree_le` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_avis_livraison_commande` (`commande_id`),
  KEY `ix_avis_livraison_livreur` (`livreur`),
  CONSTRAINT `fk_avis_livraison_commande` FOREIGN KEY (`commande_id`) REFERENCES `commandes` (`id`),
  CONSTRAINT `ck_avis_livraison_note` CHECK (`note` BETWEEN 1 AND 5)
);
