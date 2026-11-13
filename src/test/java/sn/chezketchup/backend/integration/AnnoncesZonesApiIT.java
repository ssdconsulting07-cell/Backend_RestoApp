package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

/** Annonces (publication, slides) et zones de livraison (regions, secteurs officiels / manuels). */
class AnnoncesZonesApiIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void regionDeTest() {
        // Region et arrondissement officiel crees en base (pas d'API de creation :
        // la liste officielle vient des donnees de reference).
        jdbc.update("INSERT IGNORE INTO regions (code, nom, active, version, modifie_le) VALUES ('TESTREG', 'Test', 1, 0, NOW(6))");
        jdbc.update("INSERT IGNORE INTO regions (code, nom, active, version, modifie_le) VALUES ('FERMEE', 'Fermee', 0, 0, NOW(6))");
        jdbc.update("INSERT IGNORE INTO secteurs (id, region_code, departement, nom, prix_livraison, centre_latitude, "
                + "centre_longitude, est_officiel, est_masque, version, modifie_le) VALUES "
                + "('TESTREG-OFFICIEL', 'TESTREG', 'Dep', 'Officiel', 1000, 14.7, -17.4, 1, 0, 0, NOW(6)),"
                + "('FERMEE-ZONE', 'FERMEE', 'Dep', 'Zone', 1000, NULL, NULL, 1, 0, 0, NOW(6))");
    }

    private static List<String> ids(JsonNode tableau) {
        return StreamSupport.stream(tableau.spliterator(), false).map(n -> n.get("id").asString()).toList();
    }

    @Test
    void annoncePublieeAvecSesSlidesEtSaDateDePublication() {
        String titre = unique("Nouveautes");
        Reponse cree = envoyer("POST", "/annonces", """
                {"titre":"%s","slides":[{"titre":"Slide 1","message":"Bonjour","imageUrl":""},
                 {"titre":"Slide 2","imageUrl":"https://images.unsplash.com/photo-2"}]}""".formatted(titre), jeton("GERANT"));
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        String id = cree.json().get("id").asString();
        assertThat(ids(get("/annonces", null).json())).doesNotContain(id);

        Reponse publiee = envoyer("PATCH", "/annonces/" + id + "/statut", "{\"statut\":\"ACTIF\"}", jeton("GERANT"));
        assertThat(publiee.json().get("publishedAt").isNull()).isFalse();
        JsonNode visible = StreamSupport.stream(get("/annonces", null).json().spliterator(), false)
                .filter(a -> a.get("id").asString().equals(id)).findFirst().orElseThrow();
        assertThat(visible.get("slides")).hasSize(2);
        assertThat(visible.get("slides").get(0).get("titre").asString()).isEqualTo("Slide 1");
        assertThat(visible.get("slides").get(0).get("imageUrl").isNull()).as("vide = sans image").isTrue();

        Reponse sansSlide = envoyer("POST", "/annonces", "{\"titre\":\"Vide\",\"slides\":[]}", jeton("GERANT"));
        assertThat(sansSlide.statut()).isEqualTo(400);
    }

    @Test
    void regionsEtZonesProposeesAuxClients() {
        assertThat(ids(get("/regions?active=true", null).json())).contains("TESTREG").doesNotContain("FERMEE");
        assertThat(ids(get("/regions/FERMEE/secteurs", null).json())).as("region fermee : rien a proposer").isEmpty();
        assertThat(get("/regions/INCONNUE/secteurs", null).statut()).isEqualTo(404);

        JsonNode officiel = get("/regions/TESTREG/secteurs", null).json().get(0);
        assertThat(officiel.get("centre").get("latitude").asDouble()).isEqualTo(14.7);
        assertThat(officiel.get("prixLivraison").isIntegralNumber()).isTrue();
    }

    @Test
    void unArrondissementOfficielSeMasqueMaisNeSeSupprimePas() {
        String gerant = jeton("GERANT");
        Reponse suppression = envoyer("DELETE", "/secteurs/TESTREG-OFFICIEL", null, gerant);
        assertThat(suppression.statut()).isEqualTo(409);
        assertThat(suppression.json().get("code").asString()).isEqualTo("SECTEUR_OFFICIEL");

        Reponse renommage = envoyer("PUT", "/secteurs/TESTREG-OFFICIEL",
                "{\"nom\":\"Autre nom\",\"prixLivraison\":1500}", gerant);
        assertThat(renommage.statut()).isEqualTo(409);

        assertThat(envoyer("PATCH", "/secteurs/TESTREG-OFFICIEL/masque", "{\"estMasque\":true}", gerant).statut())
                .isEqualTo(200);
        assertThat(ids(get("/regions/TESTREG/secteurs", null).json())).doesNotContain("TESTREG-OFFICIEL");
        assertThat(ids(get("/regions/TESTREG/secteurs", gerant).json())).as("le Gerant voit les zones masquees")
                .contains("TESTREG-OFFICIEL");
        envoyer("PATCH", "/secteurs/TESTREG-OFFICIEL/masque", "{\"estMasque\":false}", gerant);
    }

    @Test
    void zoneAjouteeALaMain() {
        String gerant = jeton("GERANT");
        Reponse cree = envoyer("POST", "/regions/TESTREG/secteurs",
                "{\"departement\":\"Dakar\",\"nom\":\"Ouakam Corniche\",\"prixLivraison\":1500}", gerant);
        assertThat(cree.statut()).as(cree.corps()).isEqualTo(201);
        assertThat(cree.json().get("id").asString()).isEqualTo("TESTREG-OUAKAM-CORNICHE");
        assertThat(cree.json().get("estOfficiel").asBoolean()).isFalse();

        assertThat(envoyer("POST", "/regions/TESTREG/secteurs",
                "{\"departement\":\"Dakar\",\"nom\":\"ouakam corniche\",\"prixLivraison\":1500}", gerant).statut())
                .isEqualTo(409);
        assertThat(envoyer("PUT", "/secteurs/TESTREG-OUAKAM-CORNICHE", "{\"prixLivraison\":2000}", gerant)
                .json().get("prixLivraison").asLong()).isEqualTo(2000);
        assertThat(envoyer("DELETE", "/secteurs/TESTREG-OUAKAM-CORNICHE", null, gerant).statut()).isEqualTo(204);
        assertThat(envoyer("DELETE", "/secteurs/TESTREG-OUAKAM-CORNICHE", null, jeton("LIVREUR")).statut())
                .isEqualTo(403);
    }

    @Test
    void ouvrirEtFermerUneRegion() {
        String gerant = jeton("GERANT");
        assertThat(envoyer("PATCH", "/regions/FERMEE", "{\"active\":true}", gerant).statut()).isEqualTo(200);
        assertThat(ids(get("/regions/FERMEE/secteurs", null).json())).contains("FERMEE-ZONE");
        envoyer("PATCH", "/regions/FERMEE", "{\"active\":false}", gerant);
        assertThat(ids(get("/regions/FERMEE/secteurs", null).json())).isEmpty();
    }
}
