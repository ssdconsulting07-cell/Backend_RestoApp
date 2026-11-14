package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

/** Avis du client sur sa livraison et note moyenne du livreur dans la gestion du personnel. */
class AvisLivraisonIT extends IntegrationTest {

    private static String produit;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void catalogue() {
        if (produit != null) {
            return;
        }
        String gerant = jeton("GERANT");
        Reponse cat = envoyer("POST", "/categories", "{\"nom\":\"" + unique("Avis") + "\"}", gerant);
        String categorie = cat.json().get("id").asString();
        envoyer("PATCH", "/categories/" + categorie + "/statut", "{\"statut\":\"ACTIF\"}", gerant);
        Reponse p = envoyer("POST", "/produits", "{\"nom\":\"Plat avis\",\"prix\":2000,\"categorieId\":\"" + categorie + "\"}", gerant);
        produit = p.json().get("id").asString();
        envoyer("PATCH", "/produits/" + produit + "/statut", "{\"statut\":\"ACTIF\"}", gerant);
        jdbc.update("INSERT IGNORE INTO regions (code, nom, active, version, modifie_le) VALUES ('AVISREG', 'Avis', 1, 0, NOW(6))");
        jdbc.update("INSERT IGNORE INTO secteurs (id, region_code, departement, nom, prix_livraison, est_officiel, est_masque, "
                + "version, modifie_le) VALUES ('AVISREG-Z', 'AVISREG', 'Dep', 'Zone', 500, 1, 0, 0, NOW(6))");
    }

    private JsonNode commande(String mode) {
        String zone = "LIVRAISON".equals(mode) ? ",\"secteurId\":\"AVISREG-Z\",\"regionLivraison\":\"AVISREG\"" : "";
        String corps = "{\"canal\":\"APP\",\"mode\":\"" + mode + "\",\"moyenPaiement\":\"wave\",\"clientPrenom\":\"Awa\","
                + "\"clientTelephone\":\"+22177" + (1000000 + (int) (Math.random() * 8999999)) + "\"" + zone
                + ",\"lignes\":[{\"produitId\":\"" + produit + "\",\"quantite\":1}]}";
        Reponse r = executer(HttpRequest.newBuilder(url("/commandes")).header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        return r.json();
    }

    private void avancer(String id, String statut, String role) {
        assertThat(envoyer("PATCH", "/commandes/" + id + "/statut", "{\"statut\":\"" + statut + "\"}", jeton(role)).statut())
                .as(statut).isEqualTo(200);
    }

    private String livrer(JsonNode c) {
        String id = c.get("id").asString();
        avancer(id, "EN_PREPARATION", "CUISINE");
        avancer(id, "PRETE", "CUISINE");
        avancer(id, "EN_LIVRAISON", "LIVREUR");
        avancer(id, "LIVREE", "LIVREUR");
        return id;
    }

    private Reponse noter(String id, String jetonSuivi, String corps) {
        HttpRequest.Builder b = HttpRequest.newBuilder(url("/commandes/" + id + "/avis")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps));
        if (jetonSuivi != null) {
            b.header("X-Jeton-Suivi", jetonSuivi);
        }
        return executer(b.build());
    }

    @Test
    void leClientNoteSaLivraisonUneSeuleFois() {
        JsonNode c = commande("LIVRAISON");
        String jetonSuivi = c.get("jetonSuivi").asString();
        String id = c.get("id").asString();

        Reponse tropTot = noter(id, jetonSuivi, "{\"note\":5}");
        assertThat(tropTot.statut()).isEqualTo(409);
        assertThat(tropTot.json().get("code").asString()).isEqualTo("COMMANDE_NON_LIVREE");

        livrer(c);

        assertThat(noter(id, null, "{\"note\":5}").statut()).as("sans jeton").isEqualTo(404);
        assertThat(noter(id, "faux", "{\"note\":5}").statut()).as("jeton faux").isEqualTo(404);
        assertThat(noter(id, jetonSuivi, "{\"note\":6}").statut()).as("note hors bornes").isEqualTo(400);
        assertThat(noter(id, jetonSuivi, "{\"note\":0}").statut()).isEqualTo(400);

        Reponse ok = noter(id, jetonSuivi, "{\"note\":4,\"commentaire\":\"  Rapide et poli  \"}");
        assertThat(ok.statut()).as(ok.corps()).isEqualTo(201);
        assertThat(ok.json().get("note").asInt()).isEqualTo(4);
        assertThat(ok.json().get("commentaire").asString()).isEqualTo("Rapide et poli");

        Reponse deux = noter(id, jetonSuivi, "{\"note\":1}");
        assertThat(deux.statut()).isEqualTo(409);
        assertThat(deux.json().get("code").asString()).isEqualTo("AVIS_DEJA_DONNE");

        Reponse lu = executer(HttpRequest.newBuilder(url("/commandes/" + id + "/avis")).header("X-Jeton-Suivi", jetonSuivi).build());
        assertThat(lu.statut()).isEqualTo(200);
        assertThat(lu.json().get("note").asInt()).isEqualTo(4);

        String livreur = jdbc.queryForObject("SELECT livreur FROM avis_livraison WHERE commande_id = ?", String.class, Long.parseLong(id));
        assertThat(livreur).as("livreur qui a valide la livraison").isEqualTo("livreur");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_evenements WHERE action = 'AVIS' AND cible_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void unRetraitNePeutPasEtreNoteCommeUneLivraison() {
        JsonNode c = commande("RETRAIT");
        String id = c.get("id").asString();
        avancer(id, "EN_PREPARATION", "CUISINE");
        avancer(id, "PRETE", "CUISINE");
        avancer(id, "RETIREE", "VENDEUR");
        assertThat(noter(id, c.get("jetonSuivi").asString(), "{\"note\":5}").statut()).isEqualTo(409);
    }

    @Test
    void laNoteMoyenneDuLivreurApparaitDansLaGestionDuPersonnel() {
        for (int note : new int[] {5, 4}) {
            JsonNode c = commande("LIVRAISON");
            String id = livrer(c);
            assertThat(noter(id, c.get("jetonSuivi").asString(), "{\"note\":" + note + "}").statut()).isEqualTo(201);
        }
        Double attendu = jdbc.queryForObject("SELECT ROUND(AVG(note), 1) FROM avis_livraison WHERE livreur = 'livreur'", Double.class);
        Integer nombre = jdbc.queryForObject("SELECT COUNT(*) FROM avis_livraison WHERE livreur = 'livreur'", Integer.class);

        JsonNode comptes = get("/staff-users", jeton("GERANT")).json();
        JsonNode livreur = StreamSupport.stream(comptes.spliterator(), false)
                .filter(u -> "livreur".equals(u.get("username").asString())).findFirst().orElseThrow();
        assertThat(livreur.get("noteMoyenne").asDouble()).isEqualTo(attendu);
        assertThat(livreur.get("nombreAvis").asLong()).isEqualTo(nombre.longValue());

        JsonNode cuisine = StreamSupport.stream(comptes.spliterator(), false)
                .filter(u -> "cuisine".equals(u.get("username").asString())).findFirst().orElseThrow();
        assertThat(cuisine.get("noteMoyenne").isNull()).isTrue();
        assertThat(cuisine.get("nombreAvis").asLong()).isZero();
    }
}
