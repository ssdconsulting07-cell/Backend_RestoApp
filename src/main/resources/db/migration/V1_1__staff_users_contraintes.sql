-- V1.1 : contraintes declarees dans l'entite StaffUser mais jamais creees en
-- base (ddl-auto=update n'ajoute pas de contrainte a une colonne existante).
-- Sans elles, deux comptes pouvaient avoir le meme identifiant.
--
-- - role en VARCHAR(20) NOT NULL : couvre aussi les anciennes bases ou
--   Hibernate avait cree un ENUM natif MySQL (cf. commentaire de StaffUser).
-- - identifiant obligatoire et unique ; la collation utf8mb4_0900_ai_ci rend
--   l'unicite insensible a la casse, comme findByUsernameIgnoreCase.
-- - empreinte BCrypt obligatoire.
--
-- Echoue si des donnees existantes violent ces regles (identifiant en double ou
-- vide) : corriger les lignes concernees puis relancer (voir README).
ALTER TABLE `staff_users`
  MODIFY `role` varchar(20) NOT NULL,
  MODIFY `username` varchar(255) NOT NULL,
  MODIFY `password_hash` varchar(255) NOT NULL,
  ADD CONSTRAINT `uk_staff_users_username` UNIQUE (`username`);
