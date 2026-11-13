-- Base telle qu'Hibernate (ddl-auto=update) l'a creee avant Flyway : meme schema que V1,
-- sans table flyway_schema_history. Sert au test de reprise (baseline).
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

-- Compte deja present avant l'arrivee de Flyway : il doit survivre a la reprise.
INSERT INTO `staff_users` (`active`, `password_hash`, `role`, `username`, `must_change_password`)
VALUES (b'1', '$2a$10$abcdefghijklmnopqrstuuR0Lq1m0t5d3P4ss3m0nt1c1Ex3mpl3', 'GERANT', 'ancien.compte', b'0');
