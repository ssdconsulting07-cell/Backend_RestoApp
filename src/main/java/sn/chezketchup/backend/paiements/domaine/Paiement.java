package sn.chezketchup.backend.paiements.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;

/** Tentative de paiement d'une commande chez un prestataire (ou au comptoir). */
@Entity
@Table(name = "paiements")
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commande_id", nullable = false)
    private long commandeId;

    @Column(nullable = false, length = 30)
    private String prestataire;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false, length = 12)
    private StatutPaiement statut;

    @Column(nullable = false)
    private long montant;

    @Column(nullable = false)
    private boolean simule;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    protected Paiement() {
    }

    public Paiement(long commandeId, String prestataire, String reference, long montant, boolean simule) {
        this.commandeId = commandeId;
        this.prestataire = prestataire;
        this.reference = reference;
        this.montant = montant;
        this.simule = simule;
        this.statut = StatutPaiement.EN_ATTENTE;
    }

    @PrePersist
    void avantCreation() {
        creeLe = Instant.now();
        modifieLe = creeLe;
    }

    @PreUpdate
    void avantModification() {
        modifieLe = Instant.now();
    }

    public void definirStatut(StatutPaiement statut) {
        this.statut = statut;
    }

    public Long getId() {
        return id;
    }

    public long getCommandeId() {
        return commandeId;
    }

    public String getPrestataire() {
        return prestataire;
    }

    public String getReference() {
        return reference;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public boolean isSimule() {
        return simule;
    }
}
