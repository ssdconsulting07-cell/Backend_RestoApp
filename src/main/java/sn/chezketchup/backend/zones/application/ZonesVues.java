package sn.chezketchup.backend.zones.application;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import sn.chezketchup.backend.zones.domaine.Region;
import sn.chezketchup.backend.zones.domaine.Secteur;

/** Representations API des zones (contrat : Region, Secteur) et saisies du Gerant. */
public final class ZonesVues {

    private ZonesVues() {
    }

    public record RegionVue(String id, String nom, boolean active, long version) {

        public static RegionVue de(Region r) {
            return new RegionVue(r.getCode(), r.getNom(), r.isActive(), r.getVersion());
        }
    }

    public record Centre(double latitude, double longitude) {
    }

    public record SecteurVue(
            String id,
            String regionId,
            String departement,
            String nom,
            long prixLivraison,
            Centre centre,
            boolean estOfficiel,
            boolean estMasque,
            long version,
            Instant updatedAt) {

        public static SecteurVue de(Secteur s) {
            Centre centre = s.getCentreLatitude() != null && s.getCentreLongitude() != null
                    ? new Centre(s.getCentreLatitude(), s.getCentreLongitude()) : null;
            return new SecteurVue(s.getId(), s.getRegionCode(), s.getDepartement(), s.getNom(), s.getPrixLivraison(),
                    centre, s.isEstOfficiel(), s.isEstMasque(), s.getVersion(), s.getModifieLe());
        }
    }

    public record ActivationRegion(@NotNull Boolean active, Long version) {
    }

    public record NouveauSecteur(
            @NotBlank @Size(max = 60) String departement,
            @NotBlank @Size(max = 80) String nom,
            @NotNull @PositiveOrZero Long prixLivraison) {
    }

    /** Arrondissement officiel : seul le prix change (nom et departement ignores s'ils sont identiques). */
    public record ModificationSecteur(
            @Size(max = 60) String departement,
            @Size(max = 80) String nom,
            @NotNull @PositiveOrZero Long prixLivraison,
            Long version) {
    }

    public record MasquageSecteur(@NotNull Boolean estMasque, Long version) {
    }
}
