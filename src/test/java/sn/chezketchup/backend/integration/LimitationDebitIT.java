package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Limitation de debit : connexion et suivi sans jeton plafonnes par IP (429 + Retry-After). */
@TestPropertySource(properties = {
        "app.limitation.connexions-par-minute=3",
        "app.limitation.suivi-par-minute=3"
})
class LimitationDebitIT extends IntegrationTest {

    @Test
    void auDelaDeLaLimiteLaConnexionEstRefuseeEn429() {
        List<Integer> statuts = new ArrayList<>();
        Reponse derniere = null;
        for (int i = 0; i < 5; i++) {
            derniere = envoyer("POST", "/auth/login", "{\"username\":\"gerant\",\"password\":\"faux\"}", null);
            statuts.add(derniere.statut());
        }
        assertThat(statuts).startsWith(401, 401, 401).endsWith(429);
        assertThat(derniere.json().get("code").asString()).isEqualTo("TROP_DE_REQUETES");
        assertThat(Integer.parseInt(derniere.entete("Retry-After"))).isBetween(1, 60);
    }

    @Test
    void leSuiviParTelephoneEstPlafonneContreLEnumerationDeNumeros() {
        List<Integer> statuts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            statuts.add(get("/commandes?clientTelephone=77000000" + i, null).statut());
        }
        assertThat(statuts).contains(429);
    }
}
