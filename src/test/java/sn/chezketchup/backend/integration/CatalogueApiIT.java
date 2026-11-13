package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Catalogue : visibilite, droits, cycle de publication, verrouillage optimiste, validation, cache. */
class CatalogueApiIT extends IntegrationTest {

    private String gerant() {
        return jeton("GERANT");
    }

    private JsonNode creerCategorie(String nom, boolean publier) {
        Reponse r = envoyer("POST", "/categories", "{\"nom\":\"" + nom + "\",\"accompagnementPropose\":true}", gerant());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        JsonNode c = r.json();
        if (publier) {
            c = statut("/categories/" + c.get("id").asString(), "ACTIF");
        }
        return c;
    }

    private JsonNode creerProduit(String nom, String categorieId, boolean publier) {
        Reponse r = envoyer("POST", "/produits", """
                {"nom":"%s","description":"Test","prix":3500,"categorieId":"%s",
                 "photoUrl":"https://images.unsplash.com/photo-1","tendance":false}""".formatted(nom, categorieId), gerant());
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        JsonNode p = r.json();
        assertThat(p.get("statut").asString()).isEqualTo("BROUILLON");
        if (publier) {
            p = statut("/produits/" + p.get("id").asString(), "ACTIF");
        }
        return p;
    }

    private JsonNode statut(String chemin, String statut) {
        Reponse r = envoyer("PATCH", chemin + "/statut", "{\"statut\":\"" + statut + "\"}", gerant());
        assertThat(r.statut()).as(r.corps()).isEqualTo(200);
        return r.json();
    }

    private static List<String> noms(Reponse r) {
        return StreamSupport.stream(r.json().spliterator(), false).map(n -> n.get("nom").asString()).toList();
    }

    @Test
    void unBrouillonNEstJamaisVisibleSansDroitsGerant() {
        String cat = creerCategorie(unique("Cat publique"), true).get("id").asString();
        String nom = unique("Brouillon secret");
        String id = creerProduit(nom, cat, false).get("id").asString();

        assertThat(noms(get("/produits", null))).doesNotContain(nom);
        assertThat(get("/produits/" + id, null).statut()).isEqualTo(404);
        assertThat(get("/produits?statut=BROUILLON", null).statut()).as("parametre d'URL").isEqualTo(403);
        assertThat(get("/produits?statut=BROUILLON", jeton("CUISINE")).statut()).isEqualTo(403);
        assertThat(noms(get("/produits?statut=BROUILLON", gerant()))).contains(nom);
    }

    @Test
    void unProduitPublieDUneCategorieNonPublieeResteCache() {
        String cat = creerCategorie(unique("Cat brouillon"), false).get("id").asString();
        String nom = unique("Produit orphelin");
        String id = creerProduit(nom, cat, true).get("id").asString();
        assertThat(noms(get("/produits", null))).doesNotContain(nom);
        assertThat(get("/produits/" + id, null).statut()).isEqualTo(404);

        statut("/categories/" + cat, "ACTIF");
        assertThat(noms(get("/produits", null))).as("cache vide a la publication").contains(nom);
        assertThat(get("/produits/" + id, null).statut()).isEqualTo(200);
    }

    @Test
    void lecturePubliqueAvecCacheHttpCourt() {
        Reponse r = get("/categories", null);
        assertThat(r.statut()).isEqualTo(200);
        assertThat(r.entete("Cache-Control")).contains("max-age=30").contains("public");
        assertThat(get("/categories", gerant()).entete("Cache-Control")).contains("no-store");
    }

    @Test
    void seulLeGerantEcrit() {
        String corps = "{\"nom\":\"Interdit\"}";
        assertThat(envoyer("POST", "/categories", corps, null).statut()).isEqualTo(401);
        assertThat(envoyer("POST", "/categories", corps, jeton("CUISINE")).statut()).isEqualTo(403);
        assertThat(envoyer("POST", "/categories", corps, jeton("MANAGER")).statut()).isEqualTo(403);
    }

    @Test
    void deuxGerantsSurLaMemeFicheLeSecondEstRefuse() {
        String cat = creerCategorie(unique("Cat version"), false).get("id").asString();
        JsonNode p = creerProduit(unique("Burger concurrent"), cat, false);
        String id = p.get("id").asString();
        long versionLue = p.get("version").asLong();
        String modif = """
                {"nom":"%s","prix":%d,"categorieId":"%s","version":%d}""";

        Reponse premier = envoyer("PUT", "/produits/" + id, modif.formatted("Version A", 3800, cat, versionLue), gerant());
        assertThat(premier.statut()).isEqualTo(200);
        Reponse second = envoyer("PUT", "/produits/" + id, modif.formatted("Version B", 4000, cat, versionLue), gerant());
        assertThat(second.statut()).isEqualTo(409);
        assertThat(second.json().get("code").asString()).isEqualTo("VERSION_CONFLICT");
        assertThat(get("/produits/" + id, gerant()).json().get("nom").asString()).isEqualTo("Version A");
    }

    @Test
    void validationDesSaisies() {
        String cat = creerCategorie(unique("Cat validation"), false).get("id").asString();
        Reponse decimal = envoyer("POST", "/produits", """
                {"nom":"Decimal","prix":3500.5,"categorieId":"%s"}""".formatted(cat), gerant());
        assertThat(decimal.statut()).as("montant non entier refuse, jamais tronque").isEqualTo(400);

        Reponse zero = envoyer("POST", "/produits", """
                {"nom":"Gratuit","prix":0,"categorieId":"%s"}""".formatted(cat), gerant());
        assertThat(zero.statut()).isEqualTo(400);
        assertThat(zero.json().get("field").asString()).isEqualTo("prix");

        Reponse script = envoyer("POST", "/produits", """
                {"nom":"XSS","prix":100,"categorieId":"%s","photoUrl":"javascript:alert(1)"}""".formatted(cat), gerant());
        assertThat(script.statut()).isEqualTo(400);
        assertThat(script.json().get("field").asString()).isEqualTo("photoUrl");

        Reponse inconnue = envoyer("POST", "/produits", "{\"nom\":\"X\",\"prix\":100,\"categorieId\":\"999999\"}", gerant());
        assertThat(inconnue.statut()).isEqualTo(400);
        assertThat(inconnue.json().get("code").asString()).isEqualTo("CATEGORIE_INCONNUE");

        String nom = unique("Doublon");
        creerCategorie(nom, false);
        assertThat(envoyer("POST", "/categories", "{\"nom\":\"" + nom.toUpperCase() + "\"}", gerant()).statut())
                .as("nom de categorie unique, insensible a la casse").isEqualTo(409);
    }

    @Test
    void archiverUneCategorieNonVideDemandeDeReaffecterSesProduits() {
        String origine = creerCategorie(unique("Cat a archiver"), true).get("id").asString();
        String cible = creerCategorie(unique("Cat cible"), true).get("id").asString();
        String produit = creerProduit(unique("A deplacer"), origine, true).get("id").asString();

        Reponse refus = envoyer("PATCH", "/categories/" + origine + "/statut", "{\"statut\":\"ARCHIVE\"}", gerant());
        assertThat(refus.statut()).isEqualTo(409);
        assertThat(refus.json().get("code").asString()).isEqualTo("CATEGORIE_NON_VIDE");

        Reponse ok = envoyer("PATCH", "/categories/" + origine + "/statut",
                "{\"statut\":\"ARCHIVE\",\"reassignerVers\":\"" + cible + "\"}", gerant());
        assertThat(ok.statut()).isEqualTo(200);
        assertThat(get("/produits/" + produit, gerant()).json().get("categorieId").asString()).isEqualTo(cible);
    }

    @Test
    void transitionsDePublication() {
        String cat = creerCategorie(unique("Cat cycle"), false).get("id").asString();
        statut("/categories/" + cat, "ARCHIVE");
        Reponse interdit = envoyer("PATCH", "/categories/" + cat + "/statut", "{\"statut\":\"ACTIF\"}", gerant());
        assertThat(interdit.statut()).as("ARCHIVE -> ACTIF : restaurer d'abord").isEqualTo(409);
        assertThat(statut("/categories/" + cat, "BROUILLON").get("statut").asString()).isEqualTo("BROUILLON");
    }

    @Test
    void platsDuJourEtAccompagnements() {
        String nomPlat = unique("Yassa test");
        Reponse plat = envoyer("POST", "/plats-du-jour", """
                {"nom":"%s","prix":3000,"jour":"MARDI"}""".formatted(nomPlat), gerant());
        assertThat(plat.statut()).isEqualTo(201);
        statut("/plats-du-jour/" + plat.json().get("id").asString(), "ACTIF");
        assertThat(noms(get("/plats-du-jour?jour=MARDI", null))).contains(nomPlat);
        assertThat(noms(get("/plats-du-jour?jour=LUNDI", null))).doesNotContain(nomPlat);
        assertThat(get("/plats-du-jour?jour=FERIE", null).statut()).isEqualTo(400);

        Reponse offert = envoyer("POST", "/accompagnements-condiments",
                "{\"nom\":\"" + unique("Piment") + "\",\"prix\":0,\"type\":\"CONDIMENT\"}", gerant());
        assertThat(offert.statut()).as("prix 0 autorise (offert)").isEqualTo(201);
    }
}
