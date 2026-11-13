package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Routes d'authentification et de gestion du personnel, de bout en bout
 * (HTTP reel -> Spring Security -> JPA -> MySQL migre par Flyway).
 */
class AuthApiIT extends IntegrationTest {

    @Test
    void connexionReussieRenvoieUnJetonEtLeRole() {
        Reponse r = envoyer("POST", "/auth/login", "{\"username\":\"gerant\",\"password\":\"Gerant-Test-2026\"}", null);
        assertThat(r.statut()).isEqualTo(200);
        assertThat(r.json().get("role").asString()).isEqualTo("GERANT");
        assertThat(r.json().get("token").asString()).isNotBlank();
    }

    @Test
    void mauvaisMotDePasse401() {
        assertThat(envoyer("POST", "/auth/login", "{\"username\":\"gerant\",\"password\":\"faux\"}", null).statut())
                .isEqualTo(401);
    }

    @Test
    void identifiantInsensibleALaCasse() {
        assertThat(envoyer("POST", "/auth/login", "{\"username\":\"GERANT\",\"password\":\"Gerant-Test-2026\"}", null)
                .statut()).isEqualTo(200);
    }

    @Test
    void gestionDuPersonnelSelonLeRole() {
        assertThat(get("/staff-users", jeton("MANAGER")).statut())
                .as("MANAGER gere son equipe (correctif fix/manager-acces-staff-users)").isEqualTo(200);
        assertThat(get("/staff-users", jeton("GERANT")).statut()).isEqualTo(200);
        assertThat(get("/staff-users", jeton("CUISINE")).statut()).isEqualTo(403);
        assertThat(get("/staff-users", null).statut()).isEqualTo(401);
    }

    @Test
    void routesPubliquesEtErreurs() {
        assertThat(get("/health", null).statut()).isEqualTo(200);
        assertThat(get("/route-inexistante", jeton("GERANT")).statut()).isEqualTo(404);
        assertThat(envoyer("POST", "/auth/login", "{pas du json", null).statut()).isEqualTo(400);
    }
}
