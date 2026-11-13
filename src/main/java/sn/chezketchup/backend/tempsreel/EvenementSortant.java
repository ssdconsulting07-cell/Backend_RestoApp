package sn.chezketchup.backend.tempsreel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Outbox : evenement ecrit DANS la transaction de la commande, publie
 * ensuite vers les postes du staff (SSE) par RelaisEvenements. Une commande
 * enregistree produit donc toujours son evenement, meme si le serveur
 * s'arrete juste apres la validation.
 */
@Entity
@Table(name = "evenements_sortants")
public class EvenementSortant {

    public enum Type { COMMANDE_PAYEE, STATUT_CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "commande_id", nullable = false)
    private long commandeId;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "publie_le")
    private Instant publieLe;

    protected EvenementSortant() {
    }

    public EvenementSortant(Type type, long commandeId, Instant creeLe) {
        this.type = type.name();
        this.commandeId = commandeId;
        this.creeLe = creeLe;
    }

    public void marquerPublie(Instant quand) {
        this.publieLe = quand;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public long getCommandeId() {
        return commandeId;
    }
}
