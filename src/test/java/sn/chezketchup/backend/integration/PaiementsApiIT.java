package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import sn.chezketchup.backend.paiements.application.SuiviPaiements;
import sn.chezketchup.backend.paiements.infrastructure.SimulatedPaymentProvider;
import tools.jackson.databind.JsonNode;

/**
 * Paiement asynchrone, comme avec un vrai prestataire : simulateur en mode
 * "webhook" (la commande attend une notification signee).
 */
@TestPropertySource(properties = {
        "app.paiement.simule.mode=webhook",
        "app.paiement.simule.secret-webhook=secret-de-test-webhook"
})
class PaiementsApiIT extends IntegrationTest {

    private static final String SECRET = "secret-de-test-webhook";
    private static String produit;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SuiviPaiements suiviPaiements;

    @BeforeEach
    void catalogue() {
        if (produit != null) {
            return;
        }
        String gerant = jeton("GERANT");
        String cat = envoyer("POST", "/categories", "{\"nom\":\"" + unique("Cat paiement") + "\"}", gerant).json().get("id").asString();
        envoyer("PATCH", "/categories/" + cat + "/statut", "{\"statut\":\"ACTIF\"}", gerant);
        produit = envoyer("POST", "/produits", "{\"nom\":\"Produit paiement\",\"prix\":4000,\"categorieId\":\"" + cat + "\"}", gerant)
                .json().get("id").asString();
        envoyer("PATCH", "/produits/" + produit + "/statut", "{\"statut\":\"ACTIF\"}", gerant);
    }

    private JsonNode commande() {
        Reponse r = envoyer("POST", "/commandes", """
                {"canal":"APP","mode":"RETRAIT","moyenPaiement":"orange-money","clientPrenom":"Fatou",
                 "clientTelephone":"77%07d","lignes":[{"produitId":"%s","quantite":1}]}"""
                .formatted((long) (Math.random() * 10_000_000), produit), null);
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        return r.json();
    }

    private String reference(String commandeId) {
        return jdbc.queryForObject("SELECT reference FROM paiements WHERE commande_id = ? ORDER BY id DESC LIMIT 1",
                String.class, Long.parseLong(commandeId));
    }

    private Reponse webhook(String evenementId, String reference, String statut, long montant, long horodatage, String secret) {
        byte[] corps = """
                {"evenementId":"%s","reference":"%s","statut":"%s","montant":%d}"""
                .formatted(evenementId, reference, statut, montant).getBytes(StandardCharsets.UTF_8);
        String h = String.valueOf(horodatage);
        return executer(HttpRequest.newBuilder(url("/paiements/webhook"))
                .header("Content-Type", "application/json")
                .header("X-Horodatage", h)
                .header("X-Signature", SimulatedPaymentProvider.signer(secret, h, corps))
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps)).build());
    }

    private Reponse webhook(String evenementId, String reference, String statut, long montant) {
        return webhook(evenementId, reference, statut, montant, System.currentTimeMillis() / 1000, SECRET);
    }

    private JsonNode suivi(JsonNode commande) {
        return executer(HttpRequest.newBuilder(url("/commandes/" + commande.get("id").asString()))
                .header("X-Jeton-Suivi", commande.get("jetonSuivi").asString()).build()).json();
    }

    private boolean visibleEnCuisine(String id) {
        return StreamSupport.stream(get("/commandes", jeton("CUISINE")).json().spliterator(), false)
                .anyMatch(c -> c.get("id").asString().equals(id));
    }

    @Test
    void laCommandeAttendLeWebhookPuisPartEnCuisineUneSeuleFois() {
        JsonNode c = commande();
        String id = c.get("id").asString();
        assertThat(c.get("statut").asString()).isEqualTo("CREEE");
        assertThat(c.get("statutPaiement").asString()).isEqualTo("en_attente");
        assertThat(visibleEnCuisine(id)).as("non payee : invisible pour la cuisine").isFalse();

        String evenement = UUID.randomUUID().toString();
        Reponse premier = webhook(evenement, reference(id), "paye", 4000);
        assertThat(premier.statut()).isEqualTo(200);
        assertThat(premier.json().get("resultat").asString()).isEqualTo("TRAITEE");
        assertThat(suivi(c).get("statut").asString()).isEqualTo("PAYEE");
        assertThat(visibleEnCuisine(id)).isTrue();

        Reponse renvoi = webhook(evenement, reference(id), "paye", 4000);
        assertThat(renvoi.statut()).isEqualTo(200);
        assertThat(renvoi.json().get("resultat").asString()).isEqualTo("DEJA_TRAITEE");
        Integer passages = jdbc.queryForObject("SELECT COUNT(*) FROM historique_statuts_commande WHERE commande_id = ? "
                + "AND statut = 'PAYEE'", Integer.class, Long.parseLong(id));
        assertThat(passages).isEqualTo(1);
    }

    @Test
    void uneNotificationNonAuthentifieeEstRefusee() {
        String id = commande().get("id").asString();
        String ref = reference(id);
        long maintenant = System.currentTimeMillis() / 1000;
        assertThat(webhook("e1", ref, "paye", 4000, maintenant, "autre-secret").statut()).as("mauvaise signature").isEqualTo(401);
        assertThat(webhook("e2", ref, "paye", 4000, maintenant - 3600, SECRET).statut()).as("rejeu ancien").isEqualTo(401);
        Reponse sansSignature = executer(HttpRequest.newBuilder(url("/paiements/webhook")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"evenementId\":\"e3\",\"reference\":\"" + ref
                        + "\",\"statut\":\"paye\",\"montant\":4000}")).build());
        assertThat(sansSignature.statut()).isEqualTo(401);
        assertThat(sansSignature.json().get("code").asString()).isEqualTo("SIGNATURE_INVALIDE");
        assertThat(jdbc.queryForObject("SELECT statut FROM commandes WHERE id = ?", String.class, Long.parseLong(id)))
                .isEqualTo("CREEE");
    }

    @Test
    void unMontantDifferentNeConfirmeJamaisLaCommande() {
        JsonNode c = commande();
        Reponse r = webhook(UUID.randomUUID().toString(), reference(c.get("id").asString()), "paye", 100);
        assertThat(r.json().get("resultat").asString()).isEqualTo("MONTANT_INCOHERENT");
        assertThat(suivi(c).get("statut").asString()).isEqualTo("CREEE");
    }

    @Test
    void unPaiementRefuseAnnuleLaCommande() {
        JsonNode c = commande();
        webhook(UUID.randomUUID().toString(), reference(c.get("id").asString()), "echoue", 4000);
        JsonNode apres = suivi(c);
        assertThat(apres.get("statut").asString()).isEqualTo("ANNULEE");
        assertThat(apres.get("statutPaiement").asString()).isEqualTo("echoue");
    }

    @Test
    void relanceDuPaiementReserveeAuDetenteurDuJeton() {
        JsonNode c = commande();
        String corps = "{\"commandeId\":\"" + c.get("id").asString() + "\"}";
        assertThat(envoyer("POST", "/paiements/init", corps, null).statut()).as("sans jeton").isEqualTo(404);
        Reponse relance = executer(HttpRequest.newBuilder(url("/paiements/init")).header("Content-Type", "application/json")
                .header("X-Jeton-Suivi", c.get("jetonSuivi").asString())
                .POST(HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(relance.statut()).as(relance.corps()).isEqualTo(200);
        assertThat(relance.json().get("paymentReference").asString()).startsWith("SIMULE-");
        assertThat(relance.json().get("simule").asBoolean()).isTrue();

        webhook(UUID.randomUUID().toString(), reference(c.get("id").asString()), "paye", 4000);
        Reponse dejaPayee = executer(HttpRequest.newBuilder(url("/paiements/init")).header("Content-Type", "application/json")
                .header("X-Jeton-Suivi", c.get("jetonSuivi").asString())
                .POST(HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(dejaPayee.statut()).isEqualTo(409);
        assertThat(dejaPayee.json().get("code").asString()).isEqualTo("PAIEMENT_DEJA_REGLE");
    }

    @Test
    void commandeExpireePuisPaiementTardifRemboursementASignaler() {
        JsonNode c = commande();
        long id = Long.parseLong(c.get("id").asString());
        jdbc.update("UPDATE commandes SET cree_le = DATE_SUB(cree_le, INTERVAL 31 MINUTE) WHERE id = ?", id);
        assertThat(suiviPaiements.expirer()).isGreaterThanOrEqualTo(1);
        assertThat(suivi(c).get("statut").asString()).isEqualTo("ANNULEE");

        Reponse tardif = webhook(UUID.randomUUID().toString(), reference(String.valueOf(id)), "paye", 4000);
        assertThat(tardif.json().get("resultat").asString()).isEqualTo("TRAITEE");
        JsonNode apres = suivi(c);
        assertThat(apres.get("statut").asString()).as("rien n'est prepare").isEqualTo("ANNULEE");
        assertThat(apres.get("statutPaiement").asString()).isEqualTo("paye");
        assertThat(jdbc.queryForObject("SELECT remboursement_a_signaler FROM commandes WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void referenceInconnueAcquitteeSansEffet() {
        Reponse r = webhook(UUID.randomUUID().toString(), "SIMULE-inconnue", "paye", 4000);
        assertThat(r.statut()).isEqualTo(200);
        assertThat(r.json().get("resultat").asString()).isEqualTo("REFERENCE_INCONNUE");
    }
}
