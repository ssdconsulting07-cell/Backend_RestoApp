package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Routes d'authentification et de gestion du personnel, de bout en bout
 * (HTTP reel -> Spring Security -> JPA -> MySQL migre par Flyway).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=secret-de-test-integration-assez-long-pour-hs256",
                "app.auth.seed.enabled=true",
                "app.auth.seed.cuisine.password=Cuisine-Test-2026",
                "app.auth.seed.gerant.password=Gerant-Test-2026",
                "app.auth.seed.manager.password=Manager-Test-2026",
                "app.auth.seed.livreur.password=Livreur-Test-2026",
                "app.auth.seed.vendeur.password=Vendeur-Test-2026"
        })
@Testcontainers
class AuthApiIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    private static final Pattern JETON = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    void connexionReussieRenvoieUnJetonEtLeRole() throws Exception {
        HttpResponse<String> r = login("gerant", "Gerant-Test-2026");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"role\":\"GERANT\"").containsPattern(JETON);
    }

    @Test
    void mauvaisMotDePasse401() throws Exception {
        assertThat(login("gerant", "faux").statusCode()).isEqualTo(401);
    }

    @Test
    void identifiantInsensibleALaCasse() throws Exception {
        assertThat(login("GERANT", "Gerant-Test-2026").statusCode()).isEqualTo(200);
    }

    @Test
    void gestionDuPersonnelSelonLeRole() throws Exception {
        assertThat(get("/staff-users", jeton("manager", "Manager-Test-2026")).statusCode())
                .as("MANAGER gere son equipe (correctif fix/manager-acces-staff-users)").isEqualTo(200);
        assertThat(get("/staff-users", jeton("gerant", "Gerant-Test-2026")).statusCode()).isEqualTo(200);
        assertThat(get("/staff-users", jeton("cuisine", "Cuisine-Test-2026")).statusCode()).isEqualTo(403);
        assertThat(get("/staff-users", null).statusCode()).isEqualTo(401);
    }

    @Test
    void routesPubliquesEtErreurs() throws Exception {
        assertThat(get("/health", null).statusCode()).isEqualTo(200);
        assertThat(get("/route-inexistante", jeton("gerant", "Gerant-Test-2026")).statusCode()).isEqualTo(404);
        HttpResponse<String> jsonMalForme = http.send(HttpRequest.newBuilder(url("/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{pas du json")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(jsonMalForme.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> login(String identifiant, String motDePasse) throws Exception {
        String corps = "{\"username\":\"" + identifiant + "\",\"password\":\"" + motDePasse + "\"}";
        return http.send(HttpRequest.newBuilder(url("/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String jeton(String identifiant, String motDePasse) throws Exception {
        Matcher m = JETON.matcher(login(identifiant, motDePasse).body());
        assertThat(m.find()).as("jeton pour " + identifiant).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> get(String chemin, String jeton) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(url(chemin)).GET();
        if (jeton != null) {
            b.header("Authorization", "Bearer " + jeton);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String chemin) {
        return URI.create("http://localhost:" + port + "/api/v1" + chemin);
    }
}
