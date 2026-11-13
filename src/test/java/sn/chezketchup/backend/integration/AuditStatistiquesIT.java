package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Journal d'audit (ecriture atomique, consultation, export) et statistiques. */
class AuditStatistiquesIT extends IntegrationTest {

    private static String categorie;
    private static String produit;

    @BeforeEach
    void catalogue() {
        if (produit != null) {
            return;
        }
        String g = jeton("GERANT");
        categorie = envoyer("POST", "/categories", "{\"nom\":\"" + unique("Cat stats") + "\"}", g).json().get("id").asString();
        envoyer("PATCH", "/categories/" + categorie + "/statut", "{\"statut\":\"ACTIF\"}", g);
        produit = envoyer("POST", "/produits", "{\"nom\":\"" + unique("Produit stats") + "\",\"prix\":2500,\"categorieId\":\""
                + categorie + "\"}", g).json().get("id").asString();
        envoyer("PATCH", "/produits/" + produit + "/statut", "{\"statut\":\"ACTIF\"}", g);
    }

    private JsonNode audit(String filtres) {
        Reponse r = get("/audit?taille=200" + filtres, jeton("MANAGER"));
        assertThat(r.statut()).as(r.corps()).isEqualTo(200);
        return r.json().get("entrees");
    }

    private static boolean contient(JsonNode entrees, String texte) {
        return StreamSupport.stream(entrees.spliterator(), false).anyMatch(e -> e.get("action").asString().contains(texte));
    }

    @Test
    void chaqueEcritureDuStaffEstTraceeAvecSonAuteur() {
        String nom = unique("Desserts audit");
        envoyer("POST", "/categories", "{\"nom\":\"" + nom + "\"}", jeton("GERANT"));
        JsonNode entree = StreamSupport.stream(audit("&role=GERANT&domaine=CATEGORIE").spliterator(), false)
                .filter(e -> e.get("action").asString().contains(nom)).findFirst().orElseThrow();
        assertThat(entree.get("action").asString()).isEqualTo("a créé la catégorie « " + nom + " »");
        assertThat(entree.get("acteur").get("identifiant").asString()).isEqualTo("gerant");
        assertThat(entree.get("acteur").get("role").asString()).isEqualTo("GERANT");
        assertThat(entree.get("domaine").asString()).isEqualTo("CATEGORIE");
        assertThat(entree.get("createdAt").asString()).isNotBlank();
    }

    @Test
    void uneActionRefuseeNeLaissePasDeTrace() {
        String nom = unique("Doublon audit");
        envoyer("POST", "/categories", "{\"nom\":\"" + nom + "\"}", jeton("GERANT"));
        assertThat(envoyer("POST", "/categories", "{\"nom\":\"" + nom + "\"}", jeton("GERANT")).statut()).isEqualTo(409);
        long traces = StreamSupport.stream(audit("&domaine=CATEGORIE").spliterator(), false)
                .filter(e -> e.get("action").asString().contains(nom)).count();
        assertThat(traces).as("une seule creation reussie, une seule trace").isEqualTo(1);
    }

    @Test
    void lesComptesDuPersonnelSontTraces() {
        String identifiant = "cheikh." + Long.toString(System.nanoTime(), 36);
        Reponse r = envoyer("POST", "/staff-users", """
                {"firstName":"Cheikh","lastName":"Ba","address":"Dakar","cni":"12345678901234","phone":"+221770000001",
                 "username":"%s","role":"LIVREUR"}""".formatted(identifiant), jeton("GERANT"));
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        JsonNode entrees = audit("&domaine=PERSONNEL");
        assertThat(contient(entrees, "a créé le compte livreur de Cheikh Ba")).isTrue();
        assertThat(envoyer("POST", "/staff-users", """
                {"firstName":"Faux","lastName":"Manager","address":"Dakar","cni":"12345678901235","phone":"+221770000002",
                 "username":"faux.manager","role":"MANAGER"}""", jeton("GERANT")).statut())
                .as("le compte Manager reste unique").isEqualTo(409);
        assertThat(StreamSupport.stream(entrees.spliterator(), false).map(e -> e.get("action").asString()))
                .as("ni CNI ni telephone ni mot de passe dans le journal")
                .noneMatch(a -> a.contains("12345678901234") || a.contains("770000001"));
    }

    @Test
    void lesCommandesClientEtLesTransitionsSontTracees() {
        Reponse c = envoyer("POST", "/commandes", """
                {"canal":"APP","mode":"RETRAIT","moyenPaiement":"wave","clientPrenom":"Awa","clientTelephone":"771112233",
                 "lignes":[{"produitId":"%s","quantite":1}]}""".formatted(produit), null);
        String id = c.json().get("id").asString();
        envoyer("PATCH", "/commandes/" + id + "/statut", "{\"statut\":\"EN_PREPARATION\"}", jeton("CUISINE"));
        JsonNode entrees = audit("&domaine=COMMANDE");
        JsonNode creation = StreamSupport.stream(entrees.spliterator(), false)
                .filter(e -> e.get("action").asString().contains("nouvelle commande #" + id)).findFirst().orElseThrow();
        assertThat(creation.get("acteur").get("identifiant").asString()).isEqualTo("client");
        assertThat(creation.get("action").asString()).as("pas de donnee personnelle du client").doesNotContain("Awa", "771112233");
        assertThat(contient(audit("&role=CUISINE"), "a passé la commande #" + id + " en préparation")).isTrue();
    }

    @Test
    void journalReserveAuManagerEtPeriodeControlee() {
        assertThat(get("/audit", jeton("GERANT")).statut()).isEqualTo(403);
        assertThat(get("/audit", null).statut()).isEqualTo(401);
        assertThat(get("/audit?du=2026-11-10&au=2026-11-01", jeton("MANAGER")).statut()).isEqualTo(400);
        assertThat(get("/audit?du=2025-01-01&au=2026-11-01", jeton("MANAGER")).statut()).isEqualTo(400);
    }

    @Test
    void exportCsvPourExcelEtLExportEstLuiMemeTrace() throws Exception {
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(url("/audit/export.csv"))
                .header("Authorization", "Bearer " + jeton("MANAGER")).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(r.headers().firstValue("Content-Disposition").orElse("")).contains("attachment").contains(".csv");
        byte[] octets = r.body();
        assertThat(octets[0] & 0xFF).as("BOM UTF-8 pour Excel").isEqualTo(0xEF);
        String csv = new String(octets, StandardCharsets.UTF_8);
        assertThat(csv).contains("Date;Employé;Identifiant;Rôle;Type;Description");
        assertThat(contient(audit("&domaine=PERSONNEL"), "a exporté le journal d'audit")).isTrue();
    }

    private long caDuJour() {
        return get("/statistiques/tableau-de-bord", jeton("MANAGER")).json().get("chiffreAffaires").get("jour").asLong();
    }

    @Test
    void chiffreDAffairesEncaisseSeulementEtVentesComptoirParVendeur() {
        long avant = caDuJour();
        envoyer("POST", "/commandes", """
                {"canal":"COMPTOIR","mode":"RETRAIT","moyenPaiement":"especes","montantRecu":5000,
                 "lignes":[{"produitId":"%s","quantite":2}]}""".formatted(produit), jeton("VENDEUR"));
        String annulee = envoyer("POST", "/commandes", """
                {"canal":"APP","mode":"RETRAIT","moyenPaiement":"wave","clientPrenom":"Bob","clientTelephone":"771112244",
                 "lignes":[{"produitId":"%s","quantite":1}]}""".formatted(produit), null).json().get("id").asString();
        envoyer("PATCH", "/commandes/" + annulee + "/statut", "{\"statut\":\"ANNULEE\"}", jeton("CUISINE"));

        JsonNode tableau = get("/statistiques/tableau-de-bord", jeton("GERANT")).json();
        assertThat(tableau.get("chiffreAffaires").get("jour").asLong() - avant)
                .as("5 000 encaisses au comptoir ; la commande payee puis annulee ne compte pas").isEqualTo(5000);
        assertThat(tableau.get("chiffreAffaires").get("septDerniersJours")).hasSize(7);
        assertThat(tableau.get("commandesDuJour").get("parStatut").get("ANNULEE").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode top = get("/statistiques/top-produits?limite=50", jeton("MANAGER")).json();
        assertThat(StreamSupport.stream(top.spliterator(), false)
                .anyMatch(p -> p.get("produitId").asString().equals(produit) && p.get("quantite").asLong() >= 2)).isTrue();

        JsonNode miennes = get("/statistiques/ventes-comptoir", jeton("VENDEUR")).json();
        assertThat(miennes).isNotEmpty();
        assertThat(StreamSupport.stream(miennes.spliterator(), false).map(v -> v.get("vendeur").asString()))
                .containsOnly("vendeur");
        assertThat(get("/statistiques/ventes-comptoir", jeton("CUISINE")).statut()).isEqualTo(403);
        assertThat(get("/statistiques/tableau-de-bord", jeton("VENDEUR")).statut()).isEqualTo(403);
    }
}
