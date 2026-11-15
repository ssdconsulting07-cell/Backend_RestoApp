package sn.chezketchup.backend.commandes.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Accompagnement choisi sur une ligne : nom et prix FIGES a la commande. */
@Entity
@Table(name = "lignes_commande_accompagnements")
public class LigneAccompagnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ligne_id", nullable = false)
    private LigneCommande ligne;

    @Column(nullable = false)
    private int position;

    @Column(name = "accompagnement_id", nullable = false)
    private long accompagnementId;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false)
    private long prix;

    protected LigneAccompagnement() {
    }

    LigneAccompagnement(LigneCommande ligne, int position, LigneCommande.AccompagnementFige a) {
        this.ligne = ligne;
        this.position = position;
        this.accompagnementId = a.id();
        this.nom = a.nom();
        this.prix = a.prix();
    }

    public long getAccompagnementId() {
        return accompagnementId;
    }

    public String getNom() {
        return nom;
    }

    public long getPrix() {
        return prix;
    }
}
