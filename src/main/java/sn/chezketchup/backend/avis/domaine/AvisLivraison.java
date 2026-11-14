package sn.chezketchup.backend.avis.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Avis du client sur la livraison d'une commande (un seul par commande, jamais modifie). */
@Entity
@Table(name = "avis_livraison")
public class AvisLivraison {

    public static final int NOTE_MIN = 1;
    public static final int NOTE_MAX = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commande_id", nullable = false, unique = true)
    private Long commandeId;

    /** Identifiant du livreur qui a valide la livraison (null si inconnu). */
    @Column
    private String livreur;

    @Column(nullable = false)
    private int note;

    @Column(length = 300)
    private String commentaire;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected AvisLivraison() {
    }

    public AvisLivraison(long commandeId, String livreur, int note, String commentaire, Instant creeLe) {
        if (note < NOTE_MIN || note > NOTE_MAX) {
            throw new IllegalArgumentException("Note hors de 1 a 5");
        }
        this.commandeId = commandeId;
        this.livreur = livreur;
        this.note = note;
        this.commentaire = commentaire == null || commentaire.isBlank() ? null : commentaire.strip();
        this.creeLe = creeLe;
    }

    public Long getId() {
        return id;
    }

    public Long getCommandeId() {
        return commandeId;
    }

    public String getLivreur() {
        return livreur;
    }

    public int getNote() {
        return note;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
