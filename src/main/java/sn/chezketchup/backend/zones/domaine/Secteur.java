package sn.chezketchup.backend.zones.domaine;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import sn.chezketchup.backend.commun.erreurs.ConflitException;

/**
 * Zone de livraison : arrondissement officiel (reference senegalZones.js du
 * Manager) ou zone ajoutee a la main par le Gerant. Le prix de la livraison
 * depend de la zone.
 *
 * Regle : un arrondissement officiel peut etre masque (plus propose aux
 * clients), jamais supprime ni renomme.
 */
@Entity
@Table(name = "secteurs")
public class Secteur {

    @Id
    @Column(length = 80)
    private String id;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(nullable = false, length = 60)
    private String departement;

    @Column(nullable = false, length = 80)
    private String nom;

    /** F CFA, entier >= 0. */
    @Column(name = "prix_livraison", nullable = false)
    private long prixLivraison;

    @Column(name = "centre_latitude")
    private Double centreLatitude;

    @Column(name = "centre_longitude")
    private Double centreLongitude;

    @Column(name = "est_officiel", nullable = false)
    private boolean estOfficiel;

    @Column(name = "est_masque", nullable = false)
    private boolean estMasque;

    @Version
    private long version;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected Secteur() {
    }

    /** Zone ajoutee a la main par le Gerant (pas de centre GPS). */
    public static Secteur manuel(String regionCode, String departement, String nom, long prixLivraison) {
        Secteur s = new Secteur();
        s.id = identifiant(regionCode, nom);
        s.regionCode = regionCode;
        s.departement = departement.trim();
        s.nom = nom.trim();
        s.prixLivraison = prixLivraison;
        return s;
    }

    @PrePersist
    @PreUpdate
    void horodater() {
        modifieLe = Instant.now();
    }

    /** "Plateau/Goree" en DAKAR -> "DAKAR-PLATEAU-GOREE" (meme forme que les donnees du Manager). */
    public static String identifiant(String regionCode, String nom) {
        String sansAccents = Normalizer.normalize(nom.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sansAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        return regionCode + "-" + slug;
    }

    public void verifierVersion(Long versionLue) {
        if (versionLue != null && versionLue != version) {
            throw new ConflitException("VERSION_CONFLICT", "Cette zone a ete modifiee entre-temps. Rechargez la page.");
        }
    }

    public void modifier(String departement, String nom, long prixLivraison) {
        if (estOfficiel && ((nom != null && !nom.trim().equals(this.nom))
                || (departement != null && !departement.trim().equals(this.departement)))) {
            throw new ConflitException("SECTEUR_OFFICIEL",
                    "Un arrondissement officiel ne se renomme pas : seul son prix de livraison se modifie.");
        }
        if (!estOfficiel) {
            if (departement != null && !departement.isBlank()) {
                this.departement = departement.trim();
            }
            if (nom != null && !nom.isBlank()) {
                this.nom = nom.trim();
            }
        }
        this.prixLivraison = prixLivraison;
    }

    public void masquer(boolean masque) {
        this.estMasque = masque;
    }

    /** Appele avant suppression : refuse pour un arrondissement officiel. */
    public void verifierSuppressible() {
        if (estOfficiel) {
            throw new ConflitException("SECTEUR_OFFICIEL",
                    "Un arrondissement officiel ne se supprime pas : masquez-le pour ne plus le proposer.");
        }
    }

    public String getId() {
        return id;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getDepartement() {
        return departement;
    }

    public String getNom() {
        return nom;
    }

    public long getPrixLivraison() {
        return prixLivraison;
    }

    public Double getCentreLatitude() {
        return centreLatitude;
    }

    public Double getCentreLongitude() {
        return centreLongitude;
    }

    public boolean isEstOfficiel() {
        return estOfficiel;
    }

    public boolean isEstMasque() {
        return estMasque;
    }

    public long getVersion() {
        return version;
    }

    public Instant getModifieLe() {
        return modifieLe;
    }
}
