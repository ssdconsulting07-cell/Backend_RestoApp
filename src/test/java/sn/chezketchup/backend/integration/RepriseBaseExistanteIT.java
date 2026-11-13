package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Reprise d'une base deja en service, creee par Hibernate avant Flyway (cas
 * de toutes les bases de dev actuelles) : Flyway la "baseline" en version 1
 * sans rejouer V1, applique V1.1, et les comptes existants sont conserves.
 */
@SpringBootTest(properties = {"app.jwt.secret=secret-de-test-integration-assez-long-pour-hs256",
        "app.taches-planifiees.actives=false"})
@Testcontainers
class RepriseBaseExistanteIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withInitScript("db/test/base-hibernate-avant-flyway.sql");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void laBaseExistanteEstBaselineePuisMigree() {
        List<Map<String, Object>> historique = jdbc.queryForList(
                "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(historique).extracting(l -> l.get("version") + ":" + l.get("type"))
                .containsExactly("1:BASELINE", "1.1:SQL", "2:SQL", "3:SQL", "4:SQL", "5:SQL", "6:SQL");
        assertThat(historique).allSatisfy(l -> assertThat(l.get("success")).isIn(true, 1, (byte) 1));
    }

    @Test
    void lesComptesExistantsSontConserves() {
        Integer nb = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff_users WHERE username = 'ancien.compte'", Integer.class);
        assertThat(nb).isEqualTo(1);
    }

    @Test
    void laContrainteDUniciteEstAjoutee() {
        Integer nb = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'staff_users' "
                        + "AND constraint_name = 'uk_staff_users_username'", Integer.class);
        assertThat(nb).isEqualTo(1);
    }
}
