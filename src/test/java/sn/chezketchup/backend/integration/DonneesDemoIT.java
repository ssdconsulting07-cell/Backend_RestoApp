package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Profil dev : les donnees de demonstration (generees depuis le Manager)
 * se chargent sur une base neuve, passent la validation Hibernate et sont
 * servies par l'API comme l'App Client les attend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.jwt.secret=secret-de-test-integration-assez-long-pour-hs256",
                // Base propre a cette classe, arretee a sa fin : pas de tache planifiee
                // qui continuerait d'interroger une base disparue.
                "app.taches-planifiees.actives=false"})
@ActiveProfiles("dev")
@Testcontainers
class DonneesDemoIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    @Autowired
    JdbcTemplate jdbc;

    @Value("${local.server.port}")
    int port;

    private JsonNode get(String chemin) throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + chemin)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        return JsonMapper.builder().build().readTree(r.body());
    }

    private int compter(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void leCatalogueDeDemoDuManagerEstCharge() {
        assertThat(compter("categories")).isEqualTo(6);
        assertThat(compter("produits")).isEqualTo(27);
        assertThat(compter("plats_du_jour")).isEqualTo(9);
        assertThat(compter("accompagnements_condiments")).isEqualTo(11);
        assertThat(compter("annonces")).isEqualTo(5);
        assertThat(compter("regions")).isEqualTo(14);
        assertThat(compter("secteurs")).isEqualTo(125);
        assertThat(jdbc.queryForObject("SELECT accompagnement_propose FROM categories WHERE nom = 'Burgers'", Boolean.class))
                .isTrue();
    }

    @Test
    void lAppClientRecoitLeMenuPublieEtLesZonesDeDakar() throws Exception {
        JsonNode produits = get("/produits?statut=ACTIF");
        assertThat(produits.size()).isGreaterThan(15);
        produits.forEach(p -> assertThat(p.get("statut").asString()).isEqualTo("ACTIF"));

        JsonNode regions = get("/regions?active=true");
        assertThat(regions.size()).isEqualTo(1);
        assertThat(regions.get(0).get("id").asString()).isEqualTo("DAKAR");

        JsonNode secteurs = get("/regions/DAKAR/secteurs");
        assertThat(secteurs.size()).isEqualTo(11);
        JsonNode almadies = null;
        for (JsonNode s : secteurs) {
            if ("DAKAR-ALMADIES".equals(s.get("id").asString())) {
                almadies = s;
            }
        }
        assertThat(almadies).as("meme identifiant que db.json de l'App Client").isNotNull();
        assertThat(almadies.get("prixLivraison").asLong()).isEqualTo(1000);
        assertThat(almadies.get("centre").get("latitude").asDouble()).isEqualTo(14.735);
    }
}
