package sn.chezketchup.backend.catalogue.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commun.publication.ElementPublie;

@Entity
@Table(name = "categories")
public class Categorie extends ElementPublie {

    @Column(nullable = false, length = 80)
    private String nom;

    /** Les produits de cette categorie proposent un accompagnement facture en plus. */
    @Column(name = "accompagnement_propose", nullable = false)
    private boolean accompagnementPropose;

    protected Categorie() {
    }

    public Categorie(String nom, boolean accompagnementPropose) {
        modifier(nom, accompagnementPropose);
    }

    public void modifier(String nom, boolean accompagnementPropose) {
        this.nom = nom.trim();
        this.accompagnementPropose = accompagnementPropose;
    }

    public String getNom() {
        return nom;
    }

    public boolean isAccompagnementPropose() {
        return accompagnementPropose;
    }
}
