package sn.chezketchup.backend.paiements.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.paiements.application.PaymentProvider.DemandePaiement;
import sn.chezketchup.backend.paiements.application.PaymentProvider.ResultatInitiation;
import sn.chezketchup.backend.paiements.domaine.Paiement;
import sn.chezketchup.backend.paiements.infrastructure.PaiementRepository;

/**
 * Paiement d'une commande, dans la transaction de la commande (rien a
 * moitie fait : commande + paiement + historique ensemble).
 *
 * Flux en ligne : la commande est creee en CREEE / en_attente, le paiement
 * est initie chez le prestataire, et seule sa confirmation (immediate pour
 * le simulateur, par webhook pour un vrai prestataire) la passe en PAYEE et
 * l'envoie en cuisine. Paiement echoue = commande annulee, rien n'est
 * prepare.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaiementService {

    private final PaymentProvider prestataire;
    private final PaiementRepository paiements;
    private final Clock horloge;

    public PaiementService(PaymentProvider prestataire, PaiementRepository paiements, Clock horloge) {
        this.prestataire = prestataire;
        this.paiements = paiements;
        this.horloge = horloge;
    }

    public record IssuePaiement(String redirectUrl, boolean simule) {
    }

    /** Commande APP : paiement en ligne chez le prestataire. */
    public IssuePaiement payerEnLigne(Commande commande) {
        ResultatInitiation r = prestataire.initier(new DemandePaiement(commande.getId(), commande.getTotal(),
                commande.getMoyenPaiement(), commande.getClientTelephone()));
        Paiement p = new Paiement(commande.getId(), prestataire.code(), r.reference(), commande.getTotal(),
                prestataire.estSimule());
        appliquer(commande, p, r.statutImmediat());
        paiements.save(p);
        return new IssuePaiement(r.redirectUrl(), prestataire.estSimule());
    }

    /**
     * Vente comptoir : encaissee par le Vendeur (especes, ou paiement mobile
     * verifie sur son terminal). La monnaie est calculee par le serveur.
     */
    public void encaisserAuComptoir(Commande commande, MoyenPaiement moyen, Long montantRecu, String vendeur) {
        if (moyen == MoyenPaiement.ESPECES) {
            commande.encaisserEspeces(montantRecu);
        }
        Paiement p = new Paiement(commande.getId(), "comptoir", "COMPTOIR-" + commande.getId(), commande.getTotal(), false);
        p.definirStatut(StatutPaiement.PAYE);
        commande.confirmerPaiement(vendeur, Instant.now(horloge));
        paiements.save(p);
    }

    private void appliquer(Commande commande, Paiement p, StatutPaiement statut) {
        if (statut == null) {
            return; // en attente du webhook du prestataire
        }
        p.definirStatut(statut);
        if (statut == StatutPaiement.PAYE) {
            commande.confirmerPaiement("systeme", Instant.now(horloge));
        } else if (statut == StatutPaiement.ECHOUE) {
            commande.echecPaiement("systeme", Instant.now(horloge));
        }
    }
}
