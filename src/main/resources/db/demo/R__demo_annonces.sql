-- Donnees de demonstration : annonces (chaines de slides).
-- GENERE par scripts/demo/generer-donnees-demo.mjs depuis Manager_RestoApp :
-- ne pas modifier a la main, relancer le script.
-- Profil dev uniquement (emplacement db/demo). Rejouable sans doublon.

INSERT INTO `annonces` (`id`, `titre`, `statut`, `publiee_le`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Nouveautés du menu', 'ACTIF', '2026-11-05 09:00:00', 0, '2026-11-05 09:00:00', '2026-11-05 09:00:00'),
  (4, 'Livraison', 'ACTIF', '2026-11-08 09:00:00', 0, '2026-11-08 09:00:00', '2026-11-08 09:00:00'),
  (5, 'Offre du week-end', 'ACTIF', '2026-11-10 09:00:00', 0, '2026-11-10 09:00:00', '2026-11-10 09:00:00'),
  (2, 'Informations pratiques', 'BROUILLON', NULL, 0, '2026-09-25 09:00:00', '2026-09-25 09:00:00'),
  (3, 'Offres terminées', 'ARCHIVE', NULL, 0, '2026-08-15 09:00:00', '2026-08-15 09:00:00')
AS nouveau
ON DUPLICATE KEY UPDATE `titre` = nouveau.`titre`, `statut` = nouveau.`statut`, `publiee_le` = nouveau.`publiee_le`, `version` = `annonces`.`version` + 1, `modifie_le` = nouveau.`modifie_le`;

-- Slides des annonces de demo : remplacees en bloc (ordre et contenu).
DELETE FROM `annonce_slides` WHERE `annonce_id` IN (1, 4, 5, 2, 3);
INSERT INTO `annonce_slides` (`id`, `annonce_id`, `position`, `titre`, `message`, `image_url`) VALUES
  (1, 1, 0, 'Nouveau : les chawarmas sont arrivés !', 'Poulet ou viande, pain libanais, crudités et sauce maison. À partir de 2 500 F CFA.', 'https://images.unsplash.com/photo-1633321702518-7feccafb94d5?auto=format&fit=crop&w=1200&q=80'),
  (2, 1, 1, 'Le jus de bouye est de retour', 'Pulpe de baobab, lait et vanille : la douceur du pays, bien fraîche.', 'https://images.unsplash.com/photo-1505252585461-04db1eb84625?auto=format&fit=crop&w=1200&q=80'),
  (3, 4, 0, 'On livre jusqu’à Rufisque et Sébikotane', 'Toute la région de Dakar est couverte. Partagez votre position à la commande : le livreur arrive directement à votre porte.', 'https://images.unsplash.com/photo-1526367790999-0150786686a2?auto=format&fit=crop&w=1200&q=80'),
  (4, 5, 0, 'Un dessert offert dès 10 000 F CFA', 'Samedi et dimanche, un thiakry ou un beignet offert pour toute commande de 10 000 F CFA et plus.', 'https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=1200&q=80'),
  (5, 2, 0, 'Horaires spéciaux Tabaski', 'Le restaurant reste ouvert toute la journée pour la fête.', 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80'),
  (6, 3, 0, 'Ancienne offre de lancement', 'Offre de lancement SenYummies — terminée.', 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=1200&q=80');
