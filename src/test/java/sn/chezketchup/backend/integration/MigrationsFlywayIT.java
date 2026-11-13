package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base neuve : Flyway cree tout le schema, Hibernate le valide
 * (ddl-auto=validate, sinon le contexte ne demarre pas), et les contraintes
 * de V1.1 sont bien en base.
 */
@SpringBootTest(properties = "app.jwt.secret=secret-de-test-integration-assez-long-pour-hs256")
@Testcontainers
class MigrationsFlywayIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void toutesLesMigrationsSontAppliquees() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "1.1");
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

    private static String insertion(String identifiant) {
        return "INSERT INTO staff_users (active, password_hash, role, username, must_change_password) "
                + "VALUES (1, '$2a$10$hash', 'CUISINE', '" + identifiant + "', 0)";
    }
}
