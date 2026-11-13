-- Donnees de demonstration : categories, produits, plats du jour, accompagnements et condiments.
-- GENERE par scripts/demo/generer-donnees-demo.mjs depuis Manager_RestoApp :
-- ne pas modifier a la main, relancer le script.
-- Profil dev uniquement (emplacement db/demo). Rejouable sans doublon.

INSERT INTO `categories` (`id`, `nom`, `statut`, `accompagnement_propose`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Burgers', 'ACTIF', b'1', 0, '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
  (2, 'Accompagnements', 'ACTIF', b'0', 0, '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
  (3, 'Boissons', 'ACTIF', b'0', 0, '2026-09-20 10:00:00', '2026-09-20 10:00:00'),
  (4, 'Desserts', 'ACTIF', b'0', 0, '2026-10-30 10:00:00', '2026-10-30 10:00:00'),
  (5, 'Wraps & Chawarmas', 'ACTIF', b'0', 0, '2026-11-02 10:00:00', '2026-11-02 10:00:00'),
  (6, 'Petit-déjeuner', 'BROUILLON', b'0', 0, '2026-11-10 10:00:00', '2026-11-10 10:00:00')
AS nouveau
ON DUPLICATE KEY UPDATE `nom` = nouveau.`nom`, `statut` = nouveau.`statut`, `accompagnement_propose` = nouveau.`accompagnement_propose`, `version` = `categories`.`version` + 1, `modifie_le` = nouveau.`modifie_le`;

INSERT INTO `produits` (`id`, `nom`, `description`, `prix`, `categorie_id`, `photo_url`, `statut`, `disponibilite`, `tendance`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Ketchup Burger Classique', 'Steak haché, cheddar, sauce maison, pain brioché.', 3500, 1, 'https://images.unsplash.com/photo-1561758033-d89a9ad46330?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-09-25 09:00:00', '2026-09-25 09:00:00'),
  (2, 'Double Ketchup Burger', 'Deux steaks, double cheddar, oignons caramélisés.', 4800, 1, 'https://images.unsplash.com/photo-1572802419224-296b0aeee0d9?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'RUPTURE', b'0', 0, '2026-09-22 09:00:00', '2026-09-22 09:00:00'),
  (3, 'Frites maison', 'Frites fraîches coupées sur place, sel fin.', 1500, 2, 'https://images.unsplash.com/photo-1615485290836-4ebcebf44aaf?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-09-18 09:00:00', '2026-09-18 09:00:00'),
  (4, 'Jus de bissap', 'Bissap frais à la menthe, peu sucré.', 1000, 3, '/images/produits/jus-de-bissap.jpg', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-09-18 09:00:00', '2026-09-18 09:00:00'),
  (5, 'Burger Poulet Croustillant', 'Filet de poulet pané, sauce épicée, salade.', 3800, 1, 'https://images.unsplash.com/photo-1606755962773-d324e0a13086?auto=format&fit=crop&w=800&q=80', 'BROUILLON', 'EN_STOCK', b'0', 0, '2026-09-30 09:00:00', '2026-09-30 09:00:00'),
  (6, 'Ketchup Burger Végétarien', 'Galette de légumes, avocat, sauce maison.', 3200, 1, 'https://images.unsplash.com/photo-1520072959219-c595dc870360?auto=format&fit=crop&w=800&q=80', 'ARCHIVE', 'RUPTURE', b'0', 0, '2026-08-10 09:00:00', '2026-08-10 09:00:00'),
  (7, 'Cheese Bacon Burger', 'Steak, double cheddar fondu, bacon de bœuf croustillant.', 4500, 1, 'https://images.unsplash.com/photo-1553979459-d2229ba7433b?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-10-12 09:00:00', '2026-10-12 09:00:00'),
  (8, 'Burger Dibi', 'Agneau grillé façon dibi, oignons, moutarde, sauce piquante.', 4200, 1, 'https://images.unsplash.com/photo-1551782450-a2132b4ba21d?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-14 09:00:00', '2026-10-14 09:00:00'),
  (9, 'Smash Burger', 'Deux steaks smashés, cheddar, pickles, sauce burger.', 4000, 1, 'https://images.unsplash.com/photo-1586190848861-99aa4a171e90?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-15 09:00:00', '2026-10-15 09:00:00'),
  (10, 'Chawarma Poulet', 'Poulet mariné, crudités, frites, sauce blanche, pain libanais.', 2500, 5, 'https://images.unsplash.com/photo-1633321702518-7feccafb94d5?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-11-02 09:00:00', '2026-11-02 09:00:00'),
  (11, 'Chawarma Viande', 'Bœuf mariné aux épices, oignons, tomates, sauce tahini.', 3000, 5, 'https://images.unsplash.com/photo-1529006557810-274b9b2fc783?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-11-02 09:00:00', '2026-11-02 09:00:00'),
  (12, 'Wrap Poulet Crispy', 'Tortilla, poulet pané, chou rouge, sauce yaourt.', 3000, 5, 'https://images.unsplash.com/photo-1626700051175-6818013e1d4f?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-11-02 09:00:00', '2026-11-02 09:00:00'),
  (13, 'Frites parmesan', 'Frites maison, parmesan râpé, persil.', 2000, 2, 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-05 09:00:00', '2026-10-05 09:00:00'),
  (14, 'Onion rings', 'Rondelles d’oignon panées et croustillantes.', 2000, 2, 'https://images.unsplash.com/photo-1639024471283-03518883512d?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-05 09:00:00', '2026-10-05 09:00:00'),
  (15, 'Tenders de poulet (x5)', 'Aiguillettes de poulet panées, sauce au choix.', 2500, 2, 'https://images.unsplash.com/photo-1562967914-608f82629710?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-05 09:00:00', '2026-10-05 09:00:00'),
  (16, 'Wings épicées (x6)', 'Ailes de poulet marinées au piment, sauce barbecue.', 3000, 2, 'https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'RUPTURE', b'0', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (17, 'Salade composée', 'Crudités de saison, œuf, maïs, vinaigrette maison.', 2500, 2, 'https://images.unsplash.com/photo-1540420773420-3366772f4999?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-05 09:00:00', '2026-10-05 09:00:00'),
  (18, 'Jus de gingembre', 'Gingembre frais pressé, citron, menthe.', 1000, 3, 'https://images.unsplash.com/photo-1546173159-315724a31696?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-09-18 09:00:00', '2026-09-18 09:00:00'),
  (19, 'Jus de bouye', 'Pulpe de baobab, lait, vanille.', 1200, 3, 'https://images.unsplash.com/photo-1505252585461-04db1eb84625?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-11-05 09:00:00', '2026-11-05 09:00:00'),
  (20, 'Coca-Cola 33 cl', 'Canette bien fraîche.', 800, 3, 'https://images.unsplash.com/photo-1554866585-cd94860890b7?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-09-18 09:00:00', '2026-09-18 09:00:00'),
  (21, 'Eau minérale 50 cl', 'Eau plate.', 500, 3, 'https://images.unsplash.com/photo-1548839140-29a749e1cf4d?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-09-18 09:00:00', '2026-09-18 09:00:00'),
  (22, 'Smoothie fruits rouges', 'Fraises, framboises, banane, yaourt.', 2000, 3, 'https://images.unsplash.com/photo-1553530666-ba11a7da3888?auto=format&fit=crop&w=800&q=80', 'BROUILLON', 'EN_STOCK', b'0', 0, '2026-11-08 09:00:00', '2026-11-08 09:00:00'),
  (23, 'Thiakry', 'Couscous de mil, lait caillé sucré, vanille.', 1500, 4, 'https://images.unsplash.com/photo-1488477181946-6428a0291777?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'1', 0, '2026-10-30 09:00:00', '2026-10-30 09:00:00'),
  (24, 'Fondant au chocolat', 'Cœur coulant au chocolat noir.', 2000, 4, 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-30 09:00:00', '2026-10-30 09:00:00'),
  (25, 'Tiramisu maison', 'Mascarpone, café, cacao.', 2500, 4, 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-30 09:00:00', '2026-10-30 09:00:00'),
  (26, 'Beignets sucrés (x4)', 'Beignets moelleux saupoudrés de sucre.', 1000, 4, 'https://images.unsplash.com/photo-1551024601-bec78aea704b?auto=format&fit=crop&w=800&q=80', 'ACTIF', 'EN_STOCK', b'0', 0, '2026-10-30 09:00:00', '2026-10-30 09:00:00'),
  (27, 'Cheesecake myrtille', 'Base biscuitée, fromage frais, coulis de myrtille.', 2500, 4, 'https://images.unsplash.com/photo-1533134242443-d4fd215305ad?auto=format&fit=crop&w=800&q=80', 'BROUILLON', 'EN_STOCK', b'0', 0, '2026-11-09 09:00:00', '2026-11-09 09:00:00')
AS nouveau
ON DUPLICATE KEY UPDATE `nom` = nouveau.`nom`, `description` = nouveau.`description`, `prix` = nouveau.`prix`, `categorie_id` = nouveau.`categorie_id`, `photo_url` = nouveau.`photo_url`, `statut` = nouveau.`statut`, `disponibilite` = nouveau.`disponibilite`, `tendance` = nouveau.`tendance`, `version` = `produits`.`version` + 1, `modifie_le` = nouveau.`modifie_le`;

INSERT INTO `plats_du_jour` (`id`, `nom`, `description`, `prix`, `photo_url`, `jour`, `statut`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Thiéboudienne', 'Riz rouge au poisson, légumes mijotés, sauce tomate maison.', 3000, 'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=800&q=80', 'LUNDI', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (2, 'Yassa poisson', 'Poisson mariné au citron et aux oignons confits, riz blanc.', 3000, 'https://images.unsplash.com/photo-1574484284002-952d92456975?auto=format&fit=crop&w=800&q=80', 'MARDI', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (3, 'Mafé bœuf', 'Bœuf mijoté à la pâte d’arachide, riz blanc.', 3200, 'https://images.unsplash.com/photo-1585937421612-70a008356fbe?auto=format&fit=crop&w=800&q=80', 'MERCREDI', 'ACTIF', 0, '2026-10-21 09:00:00', '2026-10-21 09:00:00'),
  (4, 'Soupou kandia', 'Sauce gombo à l’huile de palme, poisson et fruits de mer, riz.', 3200, 'https://images.unsplash.com/photo-1604329760661-e71dc83f8f26?auto=format&fit=crop&w=800&q=80', 'JEUDI', 'ACTIF', 0, '2026-11-03 09:00:00', '2026-11-03 09:00:00'),
  (5, 'Thiou poulet', 'Poulet en sauce tomate et légumes, riz blanc.', 2800, 'https://images.unsplash.com/photo-1565557623262-b51c2513a641?auto=format&fit=crop&w=800&q=80', 'JEUDI', 'ACTIF', 0, '2026-11-03 09:00:00', '2026-11-03 09:00:00'),
  (6, 'Poisson braisé', 'Poisson entier grillé, oignons, sauce piquante, frites ou attiéké.', 3500, 'https://images.unsplash.com/photo-1519708227418-c8fd9a32b7a2?auto=format&fit=crop&w=800&q=80', 'VENDREDI', 'ACTIF', 0, '2026-11-03 09:00:00', '2026-11-03 09:00:00'),
  (7, 'Thiébou yapp', 'Riz à la viande de bœuf, oignons et épices.', 3000, NULL, 'VENDREDI', 'BROUILLON', 0, '2026-11-10 09:00:00', '2026-11-10 09:00:00'),
  (8, 'Dibi agneau', 'Agneau grillé au feu de bois, oignons, moutarde, pain.', 4000, 'https://images.unsplash.com/photo-1529193591184-b1d58069ecdd?auto=format&fit=crop&w=800&q=80', 'SAMEDI', 'ACTIF', 0, '2026-11-03 09:00:00', '2026-11-03 09:00:00'),
  (9, 'Poulet braisé frites', 'Demi-poulet braisé, frites maison, salade.', 3500, 'https://images.unsplash.com/photo-1598103442097-8b74394b95c6?auto=format&fit=crop&w=800&q=80', 'DIMANCHE', 'ACTIF', 0, '2026-11-03 09:00:00', '2026-11-03 09:00:00')
AS nouveau
ON DUPLICATE KEY UPDATE `nom` = nouveau.`nom`, `description` = nouveau.`description`, `prix` = nouveau.`prix`, `photo_url` = nouveau.`photo_url`, `jour` = nouveau.`jour`, `statut` = nouveau.`statut`, `version` = `plats_du_jour`.`version` + 1, `modifie_le` = nouveau.`modifie_le`;

INSERT INTO `accompagnements_condiments` (`id`, `nom`, `prix`, `type`, `statut`, `version`, `cree_le`, `modifie_le`) VALUES
  (1, 'Frites maison', 1500, 'ACCOMPAGNEMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (2, 'Salade', 400, 'ACCOMPAGNEMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (3, 'Riz', 500, 'ACCOMPAGNEMENT', 'BROUILLON', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (4, 'Mayonnaise', 0, 'CONDIMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (5, 'Ketchup', 0, 'CONDIMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (6, 'Alloco', 1000, 'ACCOMPAGNEMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (7, 'Attiéké', 800, 'ACCOMPAGNEMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (8, 'Sauce piquante maison', 0, 'CONDIMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (9, 'Sauce oignon (yassa)', 200, 'CONDIMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (10, 'Sauce barbecue', 200, 'CONDIMENT', 'ACTIF', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00'),
  (11, 'Moutarde', 0, 'CONDIMENT', 'ARCHIVE', 0, '2026-10-20 09:00:00', '2026-10-20 09:00:00')
AS nouveau
ON DUPLICATE KEY UPDATE `nom` = nouveau.`nom`, `prix` = nouveau.`prix`, `type` = nouveau.`type`, `statut` = nouveau.`statut`, `version` = `accompagnements_condiments`.`version` + 1, `modifie_le` = nouveau.`modifie_le`;
