package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base neuve : Flyway cree tout le schema, Hibernate le valide
 * (ddl-auto=validate, sinon le contexte ne demarre pas), et les contraintes
 * sont bien en base.
 */
class MigrationsFlywayIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void toutesLesMigrationsSontAppliquees() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "1.1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    void pasDeDonneesDeDemoHorsProfilDev() {
        Integer nb = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE 'R__demo%'", Integer.class);
        assertThat(nb).isZero();
    }

    @Test
    void zonesOfficiellesLivreesFermeesPourLaProduction() {
        // Donnees de reference (V6), presentes meme sans le profil dev : toutes les
        // regions fermees tant que le Gerant ne les ouvre pas.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regions WHERE code IN ('DAKAR', 'THIES', 'KOLDA')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM secteurs WHERE est_officiel = 1 AND region_code = 'DAKAR'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regions WHERE code = 'DAKAR' AND active = 0", Integer.class)).isEqualTo(1);
    }

    @Test
    void identifiantUniqueSansTenirCompteDeLaCasse() {
        jdbc.update(insertion("awa.ndiaye"));
        assertThatThrownBy(() -> jdbc.update(insertion("Awa.Ndiaye")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void identifiantEtEmpreinteObligatoires() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO staff_users (active, password_hash, role, username, must_change_password) "
                        + "VALUES (1, NULL, 'CUISINE', 'sans.hash', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lesMontantsNegatifsSontRefusesParLaBase() {
        jdbc.update("INSERT INTO categories (nom, statut, accompagnement_propose, version, cree_le, modifie_le) "
                + "VALUES ('Categorie contrainte', 'BROUILLON', 0, 0, NOW(6), NOW(6))");
        Long categorie = jdbc.queryForObject("SELECT id FROM categories WHERE nom = 'Categorie contrainte'", Long.class);
        // Derniere barriere (les services valident avant) : MySQL refuse via la
        // contrainte CHECK. Spring classe l'erreur MySQL 3819 en exception SQL
        // generique, d'ou la verification sur le nom de la contrainte.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO produits (nom, prix, categorie_id, statut, disponibilite, "
                + "tendance, version, cree_le, modifie_le) VALUES ('Prix nul', 0, ?, 'BROUILLON', 'EN_STOCK', 0, 0, NOW(6), NOW(6))",
                categorie)).hasMessageContaining("ck_produits_prix");
    }

    private static String insertion(String identifiant) {
        return "INSERT INTO staff_users (active, password_hash, role, username, must_change_password) "
                + "VALUES (1, '$2a$10$hash', 'CUISINE', '" + identifiant + "', 0)";
    }
}
