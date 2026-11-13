package sn.chezketchup.backend.catalogue.domaine;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commun.publication.ElementPublie;

/** Fiche independante du catalogue, rattachee a UN jour de la semaine. */
@Entity
@Table(name = "plats_du_jour")
public class PlatDuJour extends ElementPublie {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private long prix;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private JourSemaine jour;

    protected PlatDuJour() {
    }

    public PlatDuJour(String nom, String description, long prix, String photoUrl, JourSemaine jour) {
        modifier(nom, description, prix, photoUrl, jour);
    }

    public void modifier(String nom, String description, long prix, String photoUrl, JourSemaine jour) {
        this.nom = nom.trim();
        this.description = Produit.vide(description);
        this.prix = prix;
        this.photoUrl = Produit.vide(photoUrl);
        this.jour = jour;
    }

    public String getNom() {
        return nom;
    }

    public String getDescription() {
        return description;
    }

    public long getPrix() {
        return prix;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public JourSemaine getJour() {
        return jour;
    }
}
