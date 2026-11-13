package sn.chezketchup.backend.annonces.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commun.publication.ElementPublie;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Chaine d'une ou plusieurs slides, affichee en popup a l'ouverture de l'App Client. */
@Entity
@Table(name = "annonces")
public class Annonce extends ElementPublie {

    @Column(nullable = false, length = 80)
    private String titre;

    /**
     * Date de (re)publication. L'App Client s'en sert pour savoir si
     * l'annonce a deja ete vue : republier la fait reapparaitre.
     */
    @Column(name = "publiee_le")
    private Instant publieeLe;

    @OneToMany(mappedBy = "annonce", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<AnnonceSlide> slides = new ArrayList<>();

    protected Annonce() {
    }

    public Annonce(String titre) {
        this.titre = titre.trim();
    }

    public void renommer(String titre) {
        this.titre = titre.trim();
    }

    /** Remplace toutes les slides, dans l'ordre donne. */
    public void remplacerSlides(List<SlideDonnees> nouvelles) {
        slides.clear();
        for (int i = 0; i < nouvelles.size(); i++) {
            SlideDonnees s = nouvelles.get(i);
            slides.add(new AnnonceSlide(this, i, s.titre(), s.message(), s.imageUrl()));
        }
    }

    @Override
    public boolean changerStatut(StatutPublication cible) {
        boolean change = super.changerStatut(cible);
        if (change && cible == StatutPublication.ACTIF) {
            publieeLe = Instant.now();
        }
        return change;
    }

    public String getTitre() {
        return titre;
    }

    public Instant getPublieeLe() {
        return publieeLe;
    }

    public List<AnnonceSlide> getSlides() {
        return slides;
    }

    public record SlideDonnees(String titre, String message, String imageUrl) {
    }
}
