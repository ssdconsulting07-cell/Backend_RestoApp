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
            Boolean tendance,
            Long version) {
    }

    public record PlatDuJour(
            @NotBlank @Size(max = 80) String nom,
            @Size(max = 300) String description,
            @NotNull @Positive Long prix,
            @UrlImage String photoUrl,
            @NotNull JourSemaine jour,
            Long version) {
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
