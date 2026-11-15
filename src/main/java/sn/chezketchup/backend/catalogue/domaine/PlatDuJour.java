package sn.chezketchup.backend.catalogue.domaine;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.publication.ElementPublie;

/**
 * Fiche independante du catalogue, proposee un ou plusieurs jours de la
 * semaine, au dejeuner, au diner ou aux deux.
 */
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

    /** Ancienne colonne (un seul jour) : premier jour choisi, gardee pour les versions precedentes. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private JourSemaine jour;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plats_du_jour_jours", joinColumns = @JoinColumn(name = "plat_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "jour", nullable = false, length = 10)
    private Set<JourSemaine> jours = new TreeSet<>();

    @Column(nullable = false)
    private boolean dejeuner = true;

    @Column(nullable = false)
    private boolean diner = true;

    protected PlatDuJour() {
    }

    public PlatDuJour(String nom, String description, long prix, String photoUrl, Collection<JourSemaine> jours,
                      boolean dejeuner, boolean diner) {
        modifier(nom, description, prix, photoUrl, jours, dejeuner, diner);
    }

    public void modifier(String nom, String description, long prix, String photoUrl, Collection<JourSemaine> jours,
                         boolean dejeuner, boolean diner) {
        if (jours == null || jours.isEmpty()) {
            throw new RequeteInvalideException("JOURS_REQUIS", "Choisissez au moins un jour.", "jours");
        }
        if (!dejeuner && !diner) {
            throw new RequeteInvalideException("SERVICE_REQUIS", "Choisissez le déjeuner, le dîner ou les deux.",
                    "dejeuner");
        }
        this.nom = nom.trim();
        this.description = Produit.vide(description);
        this.prix = prix;
        this.photoUrl = Produit.vide(photoUrl);
        Set<JourSemaine> tries = EnumSet.copyOf(jours);
        this.jours.clear();
        this.jours.addAll(tries);
        this.jour = tries.iterator().next();
        this.dejeuner = dejeuner;
        this.diner = diner;
    }

    /** Propose ce jour-la, a ce service. */
    public boolean proposeA(JourSemaine j, Service service) {
        return jours.contains(j) && (service == Service.DEJEUNER ? dejeuner : diner);
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

    /** Premier jour (ancien champ du contrat). */
    public JourSemaine getJour() {
        return jour;
    }

    public Set<JourSemaine> getJours() {
        return jours.isEmpty() ? EnumSet.noneOf(JourSemaine.class) : EnumSet.copyOf(jours);
    }

    public boolean isDejeuner() {
        return dejeuner;
    }

    public boolean isDiner() {
        return diner;
    }
}
