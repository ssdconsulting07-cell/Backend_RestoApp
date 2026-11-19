package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import sn.chezketchup.backend.photos.ImagesDeTest;
import sn.chezketchup.backend.restaurant.DonneesImport;
import sn.chezketchup.backend.restaurant.ImportRestaurant;

/**
 * Import des donnees d'un restaurant (outil-client) : tout est cree au premier
 * passage, rejouer ne cree aucun doublon, une fiche modifiee ou supprimee par
 * le Gerant n'est jamais ecrasee, sauf passage "forcer". Les comptes du
 * personnel ne sont jamais modifies.
 */
class ImportRestaurantIT extends IntegrationTest {

    private static final String MDP_TEMPORAIRE = "Temporaire-Import-2026";
    private static final String HASH = new BCryptPasswordEncoder().encode(MDP_TEMPORAIRE);

    @Autowired
    ImportRestaurant importRestaurant;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path dossier;

    @BeforeEach
    void regionEtPhoto() throws Exception {
        jdbc.update("INSERT IGNORE INTO regions (code, nom, active, version, modifie_le) VALUES ('IMPREG', 'Import', 0, 0, NOW(6))");
        jdbc.update("INSERT IGNORE INTO secteurs (id, region_code, departement, nom, prix_livraison, centre_latitude, "
                + "centre_longitude, est_officiel, est_masque, version, modifie_le) VALUES "
                + "('IMPREG-CENTRE', 'IMPREG', 'Dep', 'Centre', 1000, 14.7, -17.4, 1, 0, 0, NOW(6)),"
                + "('IMPREG-LOIN', 'IMPREG', 'Dep', 'Loin', 1000, 14.8, -17.3, 1, 0, 0, NOW(6))");
        Files.createDirectories(dossier.resolve("photos"));
        Files.write(dossier.resolve("photos/plat.jpg"), ImagesDeTest.jpeg(400, 300));
    }

    /** Jeu de donnees d'un restaurant fictif ; {@code n} rend noms et cles uniques par test. */
    private static DonneesImport donnees(String n, String jeton, boolean forcer) {
        return new DonneesImport(DonneesImport.FORMAT, "test-" + n, jeton, forcer,
                List.of(new DonneesImport.Horaire("DEJEUNER", "11:00", "15:00")),
                List.of(new DonneesImport.Categorie(n + "-plats", "Plats " + n, true, "ACTIF")),
                List.of(new DonneesImport.Produit(n + "-yassa", "Yassa " + n, "Poulet, oignons, citron.", 3500,
                                n + "-plats", "photos/plat.jpg", "OUI", "EN_STOCK", "ACTIF"),
                        new DonneesImport.Produit(n + "-mafe", "Mafé " + n, null, 3000, n + "-plats",
                                "https://images.unsplash.com/photo-test", "AUTO", "RUPTURE", "BROUILLON")),
                List.of(new DonneesImport.Accompagnement(n + "-alloco", "Alloco " + n, 500, "ACCOMPAGNEMENT", "ACTIF")),
                List.of(new DonneesImport.PlatDuJour(n + "-thieb", "Thiéboudienne " + n, null, 4000, "photos/plat.jpg",
                        List.of("LUNDI", "JEUDI"), true, false, "ACTIF")),
                List.of(new DonneesImport.Annonce(n + "-lancement", "Ouverture " + n, "ACTIF",
                        List.of(new DonneesImport.Slide("Bienvenue", "-10 % la première semaine", "photos/plat.jpg")))),
                List.of(new DonneesImport.Region("IMPREG", true, List.of(
                        new DonneesImport.Secteur("IMPREG-CENTRE", null, null, 1500),
                        new DonneesImport.Secteur(null, "Dep", "Quartier " + n, 2000)))),
                List.of(new DonneesImport.Membre("gerant-" + n, "GERANT", "Awa", "Diop", "+221770000001", HASH)));
    }

    private int compter(String table, String colonne, String valeur) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + colonne + " = ?", Integer.class, valeur);
    }

    @Test
    void premierPassageCreeToutRejouerNeCreeAucunDoublon() {
        String n = Long.toString(System.nanoTime(), 36);
        ImportRestaurant.Bilan bilan = importRestaurant.importer(donnees(n, "j1-" + n, false), dossier);
        // 8 fiches propres a ce test (categorie, 2 produits, accompagnement, plat,
        // annonce, zone ajoutee, compte) ; horaires et region sont partages entre tests.
        assertThat(bilan.crees()).isGreaterThanOrEqualTo(8);

        assertThat(compter("categories", "nom", "Plats " + n)).isEqualTo(1);
        assertThat(compter("produits", "nom", "Yassa " + n)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT photo_url FROM produits WHERE nom = ?", String.class, "Yassa " + n))
                .as("photo du dossier deposee comme un envoi du Manager").startsWith("/api/v1/photos/");
        assertThat(jdbc.queryForObject("SELECT statut FROM produits WHERE nom = ?", String.class, "Mafé " + n))
                .isEqualTo("BROUILLON");
        assertThat(jdbc.queryForObject("SELECT statut FROM produits WHERE nom = ?", String.class, "Yassa " + n))
                .isEqualTo("ACTIF");
        assertThat(jdbc.queryForObject("SELECT diner FROM plats_du_jour WHERE nom = ?", Boolean.class, "Thiéboudienne " + n))
                .isFalse();
        assertThat(compter("annonces", "titre", "Ouverture " + n)).isEqualTo(1);

        // Zones : region ouverte, prix officiel aligne, zone ajoutee, les autres masquees.
        assertThat(jdbc.queryForObject("SELECT active FROM regions WHERE code = 'IMPREG'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT prix_livraison FROM secteurs WHERE id = 'IMPREG-CENTRE'", Long.class))
                .isEqualTo(1500L);
        assertThat(jdbc.queryForObject("SELECT est_masque FROM secteurs WHERE id = 'IMPREG-LOIN'", Boolean.class)).isTrue();
        assertThat(compter("secteurs", "nom", "Quartier " + n)).isEqualTo(1);

        // Compte du personnel : mot de passe temporaire, a changer a la premiere connexion.
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM staff_users WHERE username = ?",
                Boolean.class, "gerant-" + n)).isTrue();
        Reponse connexion = envoyer("POST", "/auth/login",
                "{\"username\":\"gerant-" + n + "\",\"password\":\"" + MDP_TEMPORAIRE + "\"}", null);
        assertThat(connexion.statut()).as(connexion.corps()).isEqualTo(200);
        assertThat(connexion.json().get("mustChangePassword").asBoolean()).isTrue();

        // Meme passage (redemarrage du conteneur) : rien.
        assertThat(importRestaurant.importer(donnees(n, "j1-" + n, false), dossier)).isNull();
        // Nouveau passage du meme dossier : aucune creation, aucun doublon.
        ImportRestaurant.Bilan rejoue = importRestaurant.importer(donnees(n, "j2-" + n, false), dossier);
        assertThat(rejoue.crees()).isZero();
        assertThat(rejoue.reimposes()).isZero();
        assertThat(compter("categories", "nom", "Plats " + n)).isEqualTo(1);
        assertThat(compter("produits", "nom", "Yassa " + n)).isEqualTo(1);
        assertThat(compter("plats_du_jour", "nom", "Thiéboudienne " + n)).isEqualTo(1);
        assertThat(compter("annonces", "titre", "Ouverture " + n)).isEqualTo(1);
        assertThat(compter("staff_users", "username", "gerant-" + n)).isEqualTo(1);
    }

    @Test
    void uneFicheModifieeOuSupprimeeParLeGerantNEstReimposeeQuAvecForcer() {
        String n = Long.toString(System.nanoTime(), 36);
        importRestaurant.importer(donnees(n, "a-" + n, false), dossier);
        String hashAvant = jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE username = ?", String.class,
                "gerant-" + n);

        // Le Gerant change un prix et supprime un accompagnement ; le titulaire change son mot de passe.
        jdbc.update("UPDATE produits SET prix = 9900 WHERE nom = ?", "Yassa " + n);
        jdbc.update("DELETE FROM accompagnements_condiments WHERE nom = ?", "Alloco " + n);
        jdbc.update("UPDATE staff_users SET password_hash = ? WHERE username = ?", hashAvant + "x", "gerant-" + n);

        importRestaurant.importer(donnees(n, "b-" + n, false), dossier);
        assertThat(jdbc.queryForObject("SELECT prix FROM produits WHERE nom = ?", Long.class, "Yassa " + n))
                .as("modification du Gerant conservee").isEqualTo(9900L);
        assertThat(compter("accompagnements_condiments", "nom", "Alloco " + n))
                .as("suppression du Gerant respectee").isZero();

        ImportRestaurant.Bilan force = importRestaurant.importer(donnees(n, "c-" + n, true), dossier);
        assertThat(force.reimposes()).isPositive();
        assertThat(jdbc.queryForObject("SELECT prix FROM produits WHERE nom = ?", Long.class, "Yassa " + n))
                .as("--forcer reimpose le fichier").isEqualTo(3500L);
        assertThat(compter("accompagnements_condiments", "nom", "Alloco " + n)).isEqualTo(1);
        assertThat(compter("produits", "nom", "Yassa " + n)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE username = ?", String.class,
                "gerant-" + n)).as("compte jamais modifie, meme force").isEqualTo(hashAvant + "x");
    }

    @Test
    void uneErreurAnnuleToutLePassageAvecUnMessageClair() {
        String n = Long.toString(System.nanoTime(), 36);
        DonneesImport base = donnees(n, "e-" + n, false);
        DonneesImport photoManquante = new DonneesImport(base.format(), base.restaurant(), base.jeton(), false,
                base.horaires(), base.categories(),
                List.of(new DonneesImport.Produit(n + "-x", "Sans photo " + n, null, 1000, n + "-plats",
                        "photos/absente.jpg", null, null, "ACTIF")),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> importRestaurant.importer(photoManquante, dossier))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("photo introuvable : photos/absente.jpg");
        assertThat(compter("categories", "nom", "Plats " + n)).as("transaction annulee").isZero();
        assertThat(compter("import_restaurant_passages", "jeton", "e-" + n)).isZero();
    }
}
