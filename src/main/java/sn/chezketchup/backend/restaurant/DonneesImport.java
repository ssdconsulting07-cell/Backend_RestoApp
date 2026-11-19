package sn.chezketchup.backend.restaurant;

import java.util.List;

/**
 * Contenu de import.json, genere par l'outil-client (outil-client/lib/import.mjs)
 * a partir du dossier du restaurant, deja valide par le schema. Chaque fiche
 * porte une {@code cle} stable (identifiant du fichier source) qui rend
 * l'import rejouable sans doublon.
 *
 * Photos : chemin relatif au dossier d'import ("photos/yassa.jpg"), URL https
 * ou image fournie avec les fronts ("/images/...").
 */
public record DonneesImport(
        int format,
        String restaurant,
        String jeton,
        boolean forcer,
        List<Horaire> horaires,
        List<Categorie> categories,
        List<Produit> produits,
        List<Accompagnement> accompagnements,
        List<PlatDuJour> platsDuJour,
        List<Annonce> annonces,
        List<Region> zones,
        List<Membre> personnel) {

    /** Version du format comprise par ce backend. */
    public static final int FORMAT = 1;

    public List<Horaire> horaires() {
        return horaires == null ? List.of() : horaires;
    }

    public List<Categorie> categories() {
        return categories == null ? List.of() : categories;
    }

    public List<Produit> produits() {
        return produits == null ? List.of() : produits;
    }

    public List<Accompagnement> accompagnements() {
        return accompagnements == null ? List.of() : accompagnements;
    }

    public List<PlatDuJour> platsDuJour() {
        return platsDuJour == null ? List.of() : platsDuJour;
    }

    public List<Annonce> annonces() {
        return annonces == null ? List.of() : annonces;
    }

    public List<Region> zones() {
        return zones == null ? List.of() : zones;
    }

    public List<Membre> personnel() {
        return personnel == null ? List.of() : personnel;
    }

    public record Horaire(String service, String debut, String fin) {
    }

    public record Categorie(String cle, String nom, boolean accompagnementPropose, String statut) {
    }

    public record Produit(String cle, String nom, String description, long prix, String categorie, String photo,
                          String tendance, String disponibilite, String statut) {
    }

    public record Accompagnement(String cle, String nom, long prix, String type, String statut) {
    }

    public record PlatDuJour(String cle, String nom, String description, long prix, String photo, List<String> jours,
                             Boolean dejeuner, Boolean diner, String statut) {
    }

    public record Annonce(String cle, String titre, String statut, List<Slide> slides) {
    }

    public record Slide(String titre, String message, String image) {
    }

    /**
     * Region livree. {@code masquerAutres} : les arrondissements officiels de la
     * region absents de la liste sont masques (le restaurant n'y livre pas).
     */
    public record Region(String code, boolean masquerAutres, List<Secteur> secteurs) {

        public List<Secteur> secteurs() {
            return secteurs == null ? List.of() : secteurs;
        }
    }

    /** {@code officiel} : identifiant d'un arrondissement officiel (DAKAR-ALMADIES), sinon zone ajoutee. */
    public record Secteur(String officiel, String departement, String nom, long frais) {
    }

    /** Mot de passe temporaire deja hache (BCrypt) par l'outil : jamais de mot de passe en clair ici. */
    public record Membre(String identifiant, String role, String prenom, String nom, String telephone,
                         String motDePasseHash) {
    }
}
