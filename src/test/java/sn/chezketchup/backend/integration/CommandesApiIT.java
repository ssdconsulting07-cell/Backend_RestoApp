package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import sn.chezketchup.backend.commun.temps.Horloge;
import tools.jackson.databind.JsonNode;

/** Commandes de bout en bout : creation, recalcul, idempotence, suivi, transitions, concurrence, temps reel. */
class CommandesApiIT extends IntegrationTest {

    private static final String[] JOURS = {"LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE"};
    private static String burgers;
    private static String burger;
    private static String frites;
    private static String boisson;
    private static String platDuJour;
    private static String platAutreJour;
    private static String rupture;

    @Autowired
    JdbcTemplate jdbc;

    private String gerant() {
        return jeton("GERANT");
    }

    private String creer(String chemin, String json) {
        Reponse r = envoyer("POST", chemin, json, gerant());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        String id = r.json().get("id").asString();
        assertThat(envoyer("PATCH", chemin + "/" + id + "/statut", "{\"statut\":\"ACTIF\"}", gerant()).statut()).isEqualTo(200);
        return id;
    }

    @BeforeEach
    void catalogue() {
        if (burger != null) {
            return;
        }
        burgers = creer("/categories", "{\"nom\":\"" + unique("Burgers cmd") + "\",\"accompagnementPropose\":true}");
        String boissons = creer("/categories", "{\"nom\":\"" + unique("Boissons cmd") + "\"}");
        burger = creer("/produits", "{\"nom\":\"Burger test\",\"prix\":3500,\"categorieId\":\"" + burgers + "\"}");
        boisson = creer("/produits", "{\"nom\":\"Bissap test\",\"prix\":1000,\"categorieId\":\"" + boissons + "\"}");
        rupture = creer("/produits", "{\"nom\":\"Rupture test\",\"prix\":2000,\"categorieId\":\"" + burgers
                + "\",\"disponibilite\":\"RUPTURE\"}");
        frites = creer("/accompagnements-condiments", "{\"nom\":\"" + unique("Frites") + "\",\"prix\":1500,\"type\":\"ACCOMPAGNEMENT\"}");
        int jour = LocalDate.now(Horloge.DAKAR).getDayOfWeek().getValue() - 1;
        platDuJour = creer("/plats-du-jour", "{\"nom\":\"Plat test\",\"prix\":3000,\"jour\":\"" + JOURS[jour] + "\"}");
        platAutreJour = creer("/plats-du-jour", "{\"nom\":\"Plat demain\",\"prix\":3000,\"jour\":\"" + JOURS[(jour + 1) % 7] + "\"}");
        jdbc.update("INSERT IGNORE INTO regions (code, nom, active, version, modifie_le) VALUES ('CMDREG', 'Cmd', 1, 0, NOW(6))");
        jdbc.update("INSERT IGNORE INTO secteurs (id, region_code, departement, nom, prix_livraison, est_officiel, est_masque, "
                + "version, modifie_le) VALUES ('CMDREG-CENTRE', 'CMDREG', 'Dep', 'Centre', 1000, 1, 0, 0, NOW(6)), "
                + "('CMDREG-MASQUE', 'CMDREG', 'Dep', 'Masque', 1000, 1, 1, 0, NOW(6))");
    }

    /** Commande telle que l'App Client l'envoie aujourd'hui (statut PAYEE et prix inclus). */
    private String commandeApp(String telephone, String lignes, long sousTotal, long frais) {
        return """
                {"canal":"APP","mode":"%s","statut":"PAYEE","moyenPaiement":"wave","statutPaiement":"paye",
                 "clientPrenom":"Awa","clientNom":"Ndiaye","clientTelephone":"%s",
                 "adresseLivraison":"Centre","regionLivraison":"CMDREG","secteurId":"CMDREG-CENTRE",
                 "positionLivraison":{"latitude":14.7167,"longitude":-17.4677,"precision":18},
                 "note":"Immeuble bleu","lignes":[%s],
                 "sousTotal":%d,"fraisLivraison":%d,"total":%d,"creeLe":"2026-11-13T09:00:00Z"}"""
                .formatted(frais > 0 ? "LIVRAISON" : "RETRAIT", telephone, lignes, sousTotal, frais, sousTotal + frais);
    }

    private String ligneBurgerFrites() {
        return "{\"produitId\":\"" + burger + "\",\"type\":\"PRODUIT\",\"nom\":\"Burger test\",\"prixUnitaire\":5000,"
                + "\"quantite\":1,\"accompagnementNom\":\"" + nomFrites() + "\",\"accompagnementPrix\":1500}";
    }

    private String nomFrites() {
        return StreamSupport.stream(get("/accompagnements-condiments", null).json().spliterator(), false)
                .filter(a -> a.get("id").asString().equals(frites)).findFirst().orElseThrow().get("nom").asString();
    }

    private static String telephone() {
        return "77" + String.format("%07d", (long) (Math.random() * 10_000_000));
    }

    private Reponse passer(String json, String cle) {
        HttpRequest.Builder b = HttpRequest.newBuilder(url("/commandes")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (cle != null) {
            b.header("Idempotency-Key", cle);
        }
        return executer(b.build());
    }

    @Test
    void commandeDeLAppClientRecalculeeEtConfirmeeParLeServeur() {
        Reponse r = passer(commandeApp(telephone(), ligneBurgerFrites() + ",{\"produitId\":\"" + boisson
                + "\",\"quantite\":2,\"prixUnitaire\":1000}", 7000, 1000), null);
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        JsonNode c = r.json();
        assertThat(Long.parseLong(c.get("id").asString())).as("numero de ticket").isGreaterThan(1000);
        assertThat(c.get("statut").asString()).as("paiement confirme par le serveur (simulateur)").isEqualTo("PAYEE");
        assertThat(c.get("statutPaiement").asString()).isEqualTo("paye");
        assertThat(c.get("paiement").get("simule").asBoolean()).isTrue();
        assertThat(c.get("total").asLong()).isEqualTo(8000);
        assertThat(c.get("adresseLivraison").asString()).as("fixee par le serveur depuis la zone").isEqualTo("Centre");
        assertThat(c.get("lignes").get(0).get("prixUnitaire").asLong()).isEqualTo(5000);
        assertThat(c.get("lignes").get(0).get("accompagnementId").asString()).isEqualTo(frites);
        assertThat(c.get("jetonSuivi").asString()).hasSizeGreaterThan(40);
        assertThat(c.get("historique")).hasSize(2);
    }

    @Test
    void unPrixOuUnTotalFalsifieEstRefuse() {
        Reponse prix = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1,\"prixUnitaire\":1}", 1, 1000), null);
        assertThat(prix.statut()).isEqualTo(400);
        assertThat(prix.json().get("code").asString()).isEqualTo("PRIX_INCOHERENT");
        assertThat(prix.json().get("field").asString()).isEqualTo("lignes[0].prixUnitaire");

        Reponse total = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 100, 1000), null);
        assertThat(total.statut()).isEqualTo(400);
        assertThat(total.json().get("code").asString()).isEqualTo("TOTAL_INCOHERENT");

        Reponse sansMontants = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 0, 1000)
                .replace("\"sousTotal\":0,\"fraisLivraison\":1000,\"total\":1000,", ""), null);
        assertThat(sansMontants.statut()).as("montants facultatifs : le serveur calcule").isEqualTo(201);
    }

    @Test
    void articlesIndisponiblesRefuses() {
        Reponse r = passer(commandeApp(telephone(), "{\"produitId\":\"" + rupture + "\",\"quantite\":1}", 2000, 1000), null);
        assertThat(r.json().get("code").asString()).isEqualTo("PRODUIT_INDISPONIBLE");
        Reponse plat = passer(commandeApp(telephone(), "{\"produitId\":\"" + platAutreJour
                + "\",\"type\":\"PLAT_DU_JOUR\",\"quantite\":1}", 3000, 1000), null);
        assertThat(plat.json().get("code").asString()).isEqualTo("PLAT_INDISPONIBLE");
        Reponse platOk = passer(commandeApp(telephone(), "{\"produitId\":\"" + platDuJour
                + "\",\"type\":\"PLAT_DU_JOUR\",\"quantite\":1}", 3000, 1000), null);
        assertThat(platOk.statut()).isEqualTo(201);
        Reponse accomp = passer(commandeApp(telephone(), "{\"produitId\":\"" + boisson + "\",\"quantite\":1,\"accompagnementNom\":\""
                + nomFrites() + "\"}", 2500, 1000), null);
        assertThat(accomp.json().get("code").asString()).isEqualTo("ACCOMPAGNEMENT_NON_PROPOSE");
        Reponse zone = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000)
                .replace("CMDREG-CENTRE", "CMDREG-MASQUE"), null);
        assertThat(zone.json().get("code").asString()).isEqualTo("ZONE_INDISPONIBLE");
        Reponse especes = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000)
                .replace("\"wave\"", "\"especes\""), null);
        assertThat(especes.json().get("code").asString()).isEqualTo("MOYEN_PAIEMENT_INVALIDE");
    }

    @Test
    void uneCommandeRenvoyeeAvecLaMemeCleNEstCreeeQuUneFois() {
        String tel = telephone();
        String json = commandeApp(tel, "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000);
        String cle = UUID.randomUUID().toString();
        Reponse premiere = passer(json, cle);
        Reponse seconde = passer(json, cle);
        assertThat(premiere.statut()).isEqualTo(201);
        assertThat(seconde.statut()).isEqualTo(201);
        assertThat(seconde.entete("Idempotency-Replayed")).isEqualTo("true");
        assertThat(seconde.json().get("id").asString()).isEqualTo(premiere.json().get("id").asString());
        assertThat(seconde.json().get("jetonSuivi").asString()).isEqualTo(premiere.json().get("jetonSuivi").asString());
        assertThat(nombreDeCommandes(tel)).isEqualTo(1);

        Reponse autre = passer(json.replace("\"quantite\":1", "\"quantite\":2").replace("3500", "7000")
                .replace("4500", "8000"), cle);
        assertThat(autre.statut()).isEqualTo(409);
        assertThat(autre.json().get("code").asString()).isEqualTo("IDEMPOTENCE_CONFLIT");
    }

    @Test
    void deuxEnvoisSimultanesAvecLaMemeCle() throws Exception {
        String tel = telephone();
        String json = commandeApp(tel, "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000);
        String cle = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Reponse>> envois = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            envois.add(pool.submit(() -> {
                depart.await();
                return passer(json, cle);
            }));
        }
        depart.countDown();
        List<Integer> statuts = new ArrayList<>();
        for (Future<Reponse> f : envois) {
            statuts.add(f.get(30, TimeUnit.SECONDS).statut());
        }
        pool.shutdown();
        assertThat(statuts).allMatch(s -> s == 201 || s == 409).contains(201);
        assertThat(nombreDeCommandes(tel)).as("une seule commande, un seul paiement").isEqualTo(1);
    }

    private int nombreDeCommandes(String telephoneLocal) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM commandes WHERE client_telephone = ?", Integer.class,
                "+221" + telephoneLocal);
    }

    @Test
    void suiviClientParJetonEtVueReduiteParTelephone() {
        String tel = telephone();
        JsonNode cree = passer(commandeApp(tel, "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000), null).json();
        String id = cree.get("id").asString();
        String jeton = cree.get("jetonSuivi").asString();

        JsonNode liste = get("/commandes?clientTelephone=" + tel, null).json();
        assertThat(liste).hasSize(1);
        JsonNode reduite = liste.get(0);
        assertThat(reduite.get("id").asString()).isEqualTo(id);
        assertThat(reduite.get("clientTelephone").asString()).as("le numero fourni par le demandeur lui-meme")
                .isEqualTo("+221" + tel);
        assertThat(reduite.has("clientPrenom")).isFalse();
        assertThat(reduite.has("positionLivraison")).as("position GPS jamais donnee sur simple numero").isFalse();
        assertThat(reduite.has("note")).isFalse();

        assertThat(get("/commandes/" + id, null).statut()).as("sans jeton").isEqualTo(404);
        assertThat(executer(HttpRequest.newBuilder(url("/commandes/" + id)).header("X-Jeton-Suivi", "faux").build()).statut())
                .isEqualTo(404);
        Reponse complete = executer(HttpRequest.newBuilder(url("/commandes/" + id)).header("X-Jeton-Suivi", jeton).build());
        assertThat(complete.statut()).isEqualTo(200);
        assertThat(complete.json().get("positionLivraison").get("latitude").asDouble()).isEqualTo(14.7167);
        assertThat(complete.json().get("historique").get(0).has("par")).as("pas de nom du staff cote client").isFalse();

        long numero = Long.parseLong(id);
        assertThat(get("/commandes/" + (numero - 1), null).statut()).as("enumeration").isEqualTo(404);
        assertThat(get("/commandes", null).statut()).as("telephone obligatoire sans jeton").isEqualTo(400);
    }

    private String commandeLivraisonPayee() {
        return passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 1000), null)
                .json().get("id").asString();
    }

    private Reponse statut(String id, String statut, String role) {
        return envoyer("PATCH", "/commandes/" + id + "/statut", "{\"statut\":\"" + statut + "\"}", jeton(role));
    }

    @Test
    void parcoursCuisineLivreurAvecHistoriqueHorodate() {
        String id = commandeLivraisonPayee();
        assertThat(statut(id, "EN_PREPARATION", "LIVREUR").statut()).as("hors de la file du livreur").isEqualTo(404);
        assertThat(statut(id, "EN_PREPARATION", "CUISINE").statut()).isEqualTo(200);
        assertThat(statut(id, "PRETE", "CUISINE").statut()).isEqualTo(200);
        assertThat(statut(id, "RETIREE", "VENDEUR").statut()).as("livraison : pas dans la file du vendeur").isEqualTo(404);
        assertThat(statut(id, "EN_LIVRAISON", "LIVREUR").statut()).isEqualTo(200);
        Reponse interdit = statut(id, "LIVREE", "CUISINE");
        assertThat(interdit.statut()).isEqualTo(403);
        assertThat(interdit.json().get("code").asString()).isEqualTo("TRANSITION_INTERDITE");
        Reponse livree = statut(id, "LIVREE", "LIVREUR");
        assertThat(livree.statut()).isEqualTo(200);
        assertThat(livree.json().get("historique")).extracting(h -> h.get("statut").asString() + ":" + h.get("par").asString())
                .containsExactly("CREEE:client", "PAYEE:systeme", "EN_PREPARATION:cuisine", "PRETE:cuisine",
                        "EN_LIVRAISON:livreur", "LIVREE:livreur");
    }

    @Test
    void cuisineEtLivreurSurLaMemeCommandeAuMemeInstant() throws Exception {
        String id = commandeLivraisonPayee();
        statut(id, "EN_PREPARATION", "CUISINE");
        statut(id, "PRETE", "CUISINE");
        String cuisine = jeton("CUISINE");
        String livreur = jeton("LIVREUR");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        Future<Reponse> a = pool.submit(() -> {
            depart.await();
            return envoyer("PATCH", "/commandes/" + id + "/statut", "{\"statut\":\"EN_LIVRAISON\"}", cuisine);
        });
        Future<Reponse> b = pool.submit(() -> {
            depart.await();
            return envoyer("PATCH", "/commandes/" + id + "/statut", "{\"statut\":\"EN_LIVRAISON\"}", livreur);
        });
        depart.countDown();
        List<Integer> statuts = List.of(a.get(30, TimeUnit.SECONDS).statut(), b.get(30, TimeUnit.SECONDS).statut());
        pool.shutdown();
        assertThat(statuts).containsExactlyInAnyOrder(200, 409);
        Integer passages = jdbc.queryForObject("SELECT COUNT(*) FROM historique_statuts_commande WHERE commande_id = ? "
                + "AND statut = 'EN_LIVRAISON'", Integer.class, Long.parseLong(id));
        assertThat(passages).as("une seule transition appliquee").isEqualTo(1);
    }

    @Test
    void annulationDUneCommandePayeeSignaleUnRemboursement() {
        String id = commandeLivraisonPayee();
        Reponse r = statut(id, "ANNULEE", "CUISINE");
        assertThat(r.statut()).isEqualTo(200);
        assertThat(r.json().get("remboursementASignaler").asBoolean()).isTrue();
    }

    @Test
    void venteAuComptoirEnEspecesMonnaieCalculeeParLeServeur() {
        String corps = """
                {"canal":"COMPTOIR","mode":"RETRAIT","moyenPaiement":"especes","montantRecu":10000,
                 "lignes":[{"produitId":"%s","quantite":1},{"produitId":"%s","quantite":2}]}""".formatted(burger, boisson);
        assertThat(envoyer("POST", "/commandes", corps, null).statut()).as("anonyme").isEqualTo(403);
        assertThat(envoyer("POST", "/commandes", corps, jeton("CUISINE")).statut()).isEqualTo(403);
        Reponse r = envoyer("POST", "/commandes", corps, jeton("VENDEUR"));
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        assertThat(r.json().get("statut").asString()).isEqualTo("PAYEE");
        assertThat(r.json().get("total").asLong()).isEqualTo(5500);
        assertThat(r.json().get("monnaieRendue").asLong()).isEqualTo(4500);
        assertThat(r.json().has("jetonSuivi")).isFalse();

        Reponse insuffisant = envoyer("POST", "/commandes", corps.replace("10000", "5000"), jeton("VENDEUR"));
        assertThat(insuffisant.json().get("code").asString()).isEqualTo("MONTANT_INSUFFISANT");
        assertThat(envoyer("POST", "/commandes", corps.replace("RETRAIT", "LIVRAISON"), jeton("VENDEUR")).statut()).isEqualTo(400);
    }

    @Test
    void chaqueRoleVoitSaFileEtSeulementLesDonneesUtiles() {
        String livraison = commandeLivraisonPayee();
        String retrait = passer(commandeApp(telephone(), "{\"produitId\":\"" + burger + "\",\"quantite\":1}", 3500, 0), null)
                .json().get("id").asString();

        JsonNode cuisine = trouver(get("/commandes", jeton("CUISINE")).json(), livraison);
        assertThat(cuisine.get("clientTelephone").asString()).startsWith("+221");
        assertThat(cuisine.has("positionLivraison")).as("la cuisine n'a pas besoin de la position").isFalse();

        assertThat(trouver(get("/commandes", jeton("LIVREUR")).json(), livraison)).as("payee, pas encore prete").isNull();
        statut(livraison, "EN_PREPARATION", "CUISINE");
        statut(livraison, "PRETE", "CUISINE");
        JsonNode livreur = trouver(get("/commandes", jeton("LIVREUR")).json(), livraison);
        assertThat(livreur.get("positionLivraison").get("latitude").asDouble()).isEqualTo(14.7167);
        assertThat(trouver(get("/commandes", jeton("LIVREUR")).json(), retrait)).isNull();

        assertThat(trouver(get("/commandes", jeton("VENDEUR")).json(), retrait)).isNotNull();
        assertThat(trouver(get("/commandes", jeton("VENDEUR")).json(), livraison)).isNull();

        JsonNode manager = trouver(get("/commandes", jeton("MANAGER")).json(), livraison);
        assertThat(manager.has("clientTelephone")).isFalse();
        assertThat(manager.has("positionLivraison")).isFalse();
    }

    private static JsonNode trouver(JsonNode liste, String id) {
        return StreamSupport.stream(liste.spliterator(), false).filter(c -> c.get("id").asString().equals(id))
                .findFirst().orElse(null);
    }

    @Test
    void tempsReelReserveAuStaffEtFiltreParRole() throws Exception {
        assertThat(get("/temps-reel/commandes", null).statut()).isEqualTo(401);

        List<String> recues = new CopyOnWriteArrayList<>();
        HttpRequest abonnement = HttpRequest.newBuilder(url("/temps-reel/commandes"))
                .header("Authorization", "Bearer " + jeton("CUISINE")).header("Accept", "text/event-stream").GET().build();
        CompletableFuture<HttpResponse<java.util.stream.Stream<String>>> flux =
                http.sendAsync(abonnement, HttpResponse.BodyHandlers.ofLines());
        HttpResponse<java.util.stream.Stream<String>> reponse = flux.get(10, TimeUnit.SECONDS);
        assertThat(reponse.statusCode()).isEqualTo(200);
        Thread lecteur = new Thread(() -> reponse.body().forEach(recues::add));
        lecteur.setDaemon(true);
        lecteur.start();
        Thread.sleep(500);

        String id = commandeLivraisonPayee();
        long fin = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < fin && recues.stream().noneMatch(l -> l.contains("\"id\":\"" + id + "\""))) {
            Thread.sleep(100);
        }
        assertThat(recues).anyMatch(l -> l.startsWith("data:") && l.contains("COMMANDE_PAYEE") && l.contains("\"id\":\"" + id + "\""));
        assertThat(recues).anyMatch(l -> l.equals("event:commande"));
    }
}
