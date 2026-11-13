package sn.chezketchup.backend.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base des tests d'integration : UNE base MySQL 8 (Testcontainers) et UN
 * contexte Spring partages par toutes les classes qui en heritent (gain de
 * temps : le conteneur ne demarre qu'une fois). Chaque test cree ses propres
 * donnees (noms uniques) et ne suppose donc jamais une base vide.
 *
 * Appels HTTP reels (client JDK) : la chaine complete est testee, filtres
 * de securite compris.
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
                "app.auth.seed.vendeur.password=Vendeur-Test-2026",
                "app.photos.dossier=target/photos-tests",
                // Les tests creent beaucoup de commandes depuis la meme IP :
                // la limitation est testee a part (LimitationDebitIT).
                "app.limitation.commandes-par-minute=100000",
                "app.limitation.suivi-par-minute=100000",
                "app.limitation.connexions-par-minute=100000"
        })
public abstract class IntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    static {
        MYSQL.start();
    }

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> JETONS = new ConcurrentHashMap<>();
    protected final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    protected int port;

    protected record Reponse(int statut, String corps, HttpResponse<String> brute) {

        JsonNode json() {
            return JSON.readTree(corps);
        }

        String entete(String nom) {
            return brute.headers().firstValue(nom).orElse(null);
        }
    }

    /** Jeton du compte de demonstration du role (mot de passe <Role>-Test-2026), mis en cache. */
    protected String jeton(String role) {
        return JETONS.computeIfAbsent(role, r -> {
            String identifiant = r.toLowerCase();
            String mdp = r.charAt(0) + r.substring(1).toLowerCase() + "-Test-2026";
            Reponse rep = envoyer("POST", "/auth/login",
                    "{\"username\":\"" + identifiant + "\",\"password\":\"" + mdp + "\"}", null);
            if (rep.statut() != 200) {
                throw new IllegalStateException("Connexion " + r + " impossible : " + rep.statut());
            }
            return rep.json().get("token").asString();
        });
    }

    protected Reponse get(String chemin, String jeton) {
        return envoyer("GET", chemin, null, jeton);
    }

    protected Reponse envoyer(String methode, String chemin, String json, String jeton) {
        HttpRequest.Builder b = HttpRequest.newBuilder(url(chemin))
                .method(methode, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (json != null) {
            b.header("Content-Type", "application/json");
        }
        if (jeton != null) {
            b.header("Authorization", "Bearer " + jeton);
        }
        return executer(b.build());
    }

    protected Reponse executer(HttpRequest requete) {
        try {
            HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
            return new Reponse(r.statusCode(), r.body(), r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected URI url(String chemin) {
        return URI.create("http://localhost:" + port + "/api/v1" + chemin);
    }

    /** Suffixe unique, pour des noms qui ne se marchent pas dessus entre tests. */
    protected static String unique(String nom) {
        return nom + " " + Long.toString(System.nanoTime(), 36);
    }
}
