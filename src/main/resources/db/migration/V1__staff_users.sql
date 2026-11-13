-- V1 : schema de depart, EXTRAIT de la base de developpement reelle
-- (mysqldump --no-data, MySQL 8.0, 13/11/2026), tel qu'Hibernate l'avait cree
-- avec ddl-auto=update. Volontairement identique a l'existant : les bases deja
-- en service sont "baselinees" en version 1 (spring.flyway.baseline-on-migrate)
-- sans rejouer ce script. Les corrections arrivent en V1_1.
CREATE TABLE `staff_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `role` varchar(20) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `must_change_password` bit(1) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `cni` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
