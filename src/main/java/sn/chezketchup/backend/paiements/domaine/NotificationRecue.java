package sn.chezketchup.backend.paiements.domaine;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;

/** Notification de prestataire deja traitee (unicite (prestataire, evenement) = traitee une seule fois). */
@Entity
@Table(name = "notifications_paiement")
public class NotificationRecue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String prestataire;

    @Column(name = "evenement_id", nullable = false, length = 100)
    private String evenementId;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false, length = 12)
    private StatutPaiement statut;

    @Column(nullable = false)
    private long montant;

    @Column(name = "recue_le", nullable = false)
    private Instant recueLe;

    protected NotificationRecue() {
    }

    public NotificationRecue(String prestataire, String evenementId, String reference, StatutPaiement statut,
                             long montant, Instant recueLe) {
        this.prestataire = prestataire;
        this.evenementId = evenementId;
        this.reference = reference;
        this.statut = statut;
        this.montant = montant;
        this.recueLe = recueLe;
    }
}
