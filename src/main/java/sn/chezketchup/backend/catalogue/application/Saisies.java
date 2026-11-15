package sn.chezketchup.backend.catalogue.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.validation.UrlImage;

/**
 * Donnees saisies par le Gerant. {@code version} = version lue avant
 * modification (verrouillage optimiste) ; facultative tant que le Manager ne
 * l'envoie pas (lot 4).
 */
public final class Saisies {

    private Saisies() {
    }

    public record Categorie(
            @NotBlank @Size(max = 80) String nom,
            Boolean accompagnementPropose,
            Long version) {
    }

    public record Produit(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 300) String description,
            @NotNull @Positive Long prix,
            @NotBlank String categorieId,
            @UrlImage String photoUrl,
            Disponibilite disponibilite,
            /** Ancien champ : badge oui / non. Prefere tendanceMode. */
            Boolean tendance,
            /** AUTO, OUI ou NON. */
            @jakarta.validation.constraints.Pattern(regexp = "AUTO|OUI|NON") String tendanceMode,
            Long version) {

        /** Badge force (TRUE / FALSE) ou automatique (null), a la creation. */
        public Boolean tendanceManuelleCreation() {
            if (tendanceMode != null) {
                return mode(tendanceMode);
            }
            return Boolean.TRUE.equals(tendance) ? Boolean.TRUE : null;
        }

        /**
         * A la modification : tendanceMode s'il est envoye ; sinon l'ancien
         * booleen ne force le badge que s'il CHANGE l'affichage (un ancien
         * Manager qui renvoie la valeur lue ne fige pas l'automatique).
         */
        public Boolean tendanceManuelleModification(Boolean actuelle, boolean affichee) {
            if (tendanceMode != null) {
                return mode(tendanceMode);
            }
            if (tendance == null || tendance == affichee) {
                return actuelle;
            }
            return tendance;
        }

        private static Boolean mode(String m) {
            return switch (m) {
                case "OUI" -> Boolean.TRUE;
                case "NON" -> Boolean.FALSE;
                default -> null;
            };
        }
    }

    public record PlatDuJour(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 300) String description,
            @NotNull @Positive Long prix,
            @UrlImage String photoUrl,
            /** Ancien champ (un seul jour), accepte si {@code jours} est absent. */
            JourSemaine jour,
            java.util.List<JourSemaine> jours,
            /** Absents = les deux services. */
            Boolean dejeuner,
            Boolean diner,
            Long version) {

        public java.util.List<JourSemaine> joursChoisis() {
            if (jours != null && !jours.isEmpty()) {
                return jours;
            }
            return jour == null ? java.util.List.of() : java.util.List.of(jour);
        }

        public boolean dejeunerChoisi() {
            return dejeuner == null || dejeuner;
        }

        public boolean dinerChoisi() {
            return diner == null || diner;
        }
    }

    public record AccompagnementCondiment(
            @NotBlank @Size(max = 80) String nom,
            @NotNull @PositiveOrZero Long prix,
            @NotNull TypeAccompagnement type,
            Long version) {
    }

    /**
     * Changement de statut. {@code reassignerVers} : categorie qui recoit les
     * produits lors de l'archivage d'une categorie non vide.
     */
    public record ChangementStatut(
            @NotNull StatutPublication statut,
            Long version,
            String reassignerVers) {
    }
}
