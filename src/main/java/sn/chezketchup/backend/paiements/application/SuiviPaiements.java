package sn.chezketchup.backend.paiements.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commandes.application.JetonSuivi;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.paiements.application.PaiementService.IssuePaiement;
import sn.chezketchup.backend.paiements.application.PaymentProvider.NotificationPaiement;
import sn.chezketchup.backend.paiements.domaine.NotificationRecue;
import sn.chezketchup.backend.paiements.domaine.Paiement;
import sn.chezketchup.backend.paiements.infrastructure.NotificationRecueRepository;
import sn.chezketchup.backend.paiements.infrastructure.PaiementRepository;
import sn.chezketchup.backend.tempsreel.EvenementSortant;
import sn.chezketchup.backend.tempsreel.EvenementSortantRepository;

/**
 * Cycle du paiement en ligne apres la creation de la commande :
 *
 * - relance (POST /paiements/init) : le client reprend le paiement d'une
 *   commande encore en attente ;
 * - notification du prestataire (webhook) : authentifiee, traitee UNE seule
 *   fois meme si le prestataire la renvoie ;
 * - expiration : une commande non payee sous 30 min est annulee (rien n'est
 *   prepare). Si le paiement arrive malgre tout ensuite : remboursement a
 *   signaler (compensation).
 *
 * Chaque operation verrouille la commande : un webhook et l'expiration qui
 * arrivent en meme temps sont servis l'un apres l'autre.
 */
@Service
public class SuiviPaiements {

    private static final Logger LOG = LoggerFactory.getLogger(SuiviPaiements.class);

    public enum Resultat { TRAITEE, DEJA_TRAITEE, REFERENCE_INCONNUE, MONTANT_INCOHERENT }

    private final PaymentProvider prestataire;
    private final PaiementService paiementService;
    private final PaiementRepository paiements;
    private final NotificationRecueRepository notifications;
    private final CommandeRepository commandes;
    private final EvenementSortantRepository evenements;
    private final JournalActions journal;
    private final Clock horloge;
    private final Duration delaiExpiration;

    public SuiviPaiements(PaymentProvider prestataire, PaiementService paiementService, PaiementRepository paiements,
                          NotificationRecueRepository notifications, CommandeRepository commandes,
                          EvenementSortantRepository evenements, JournalActions journal, Clock horloge,
                          @Value("${app.paiement.delai-expiration-minutes:30}") long delaiExpirationMinutes) {
        this.prestataire = prestataire;
        this.paiementService = paiementService;
        this.paiements = paiements;
        this.notifications = notifications;
        this.commandes = commandes;
        this.evenements = evenements;
        this.journal = journal;
        this.horloge = horloge;
        this.delaiExpiration = Duration.ofMinutes(delaiExpirationMinutes);
    }

    /** Reponse de POST /paiements/init (contrat : PaiementInit, enrichi). */
    public record PaiementInitVue(String paymentReference, String redirectUrl, StatutPaiement statutPaiement,
                                  StatutCommande statut, boolean simule) {
    }

    /**
     * Relance du paiement d'une commande APP encore en attente. Reservee au
     * detenteur du jeton de suivi (sinon 404 : personne ne declenche de
     * paiement sur la commande d'un autre).
     */
    @Transactional
    public PaiementInitVue relancer(long commandeId, String jeton) {
        Commande c = commandes.verrouiller(commandeId)
                .filter(x -> x.getCanal() == Canal.APP)
                .filter(x -> JetonSuivi.correspond(jeton, x.getJetonSuiviEmpreinte()))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande introuvable."));
        if (c.getStatut() == StatutCommande.ANNULEE) {
            throw new ConflitException("COMMANDE_ANNULEE", "Cette commande a ete annulee : passez une nouvelle commande.");
        }
        if (c.getStatut() != StatutCommande.CREEE) {
            throw new ConflitException("PAIEMENT_DEJA_REGLE", "Cette commande est deja payee.");
        }
        IssuePaiement issue = paiementService.payerEnLigne(c);
        if (c.getStatut() == StatutCommande.PAYEE) {
            evenements.save(new EvenementSortant(EvenementSortant.Type.COMMANDE_PAYEE, c.getId(), Instant.now(horloge)));
        }
        journal.enregistrer(Domaine.PAIEMENT, "RELANCE", String.valueOf(c.getId()),
                "paiement relancé pour la commande #" + c.getId());
        return new PaiementInitVue(issue.reference(), issue.redirectUrl(), c.getStatutPaiement(), c.getStatut(), issue.simule());
    }

    /** Notification du prestataire. Signature verifiee par le prestataire (401 sinon). */
    @Transactional
    public Resultat traiterNotification(byte[] corps, Map<String, String> entetes) {
        NotificationPaiement n = prestataire.lireNotification(corps, entetes);
        Instant maintenant = Instant.now(horloge);
        if (notifications.existsByPrestataireAndEvenementId(prestataire.code(), n.evenementId())) {
            return Resultat.DEJA_TRAITEE;
        }
        notifications.saveAndFlush(new NotificationRecue(prestataire.code(), n.evenementId(), n.reference(), n.statut(),
                n.montant(), maintenant));

        Paiement p = paiements.findByPrestataireAndReference(prestataire.code(), n.reference()).orElse(null);
        if (p == null) {
            LOG.warn("Notification de paiement pour une reference inconnue (evenement {})", n.evenementId());
            return Resultat.REFERENCE_INCONNUE;
        }
        Commande c = commandes.verrouiller(p.getCommandeId()).orElseThrow();
        if (n.montant() != c.getTotal()) {
            // Jamais de confirmation sur un montant different de celui de la commande.
            journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "ANOMALIE", String.valueOf(c.getId()), "paiement de " + fcfa(n.montant())
                    + " notifié pour la commande #" + c.getId() + " (attendu : " + fcfa(c.getTotal()) + ") : ignoré");
            return Resultat.MONTANT_INCOHERENT;
        }
        p.definirStatut(n.statut());
        if (n.statut() == StatutPaiement.PAYE) {
            if (c.getStatut() == StatutCommande.CREEE) {
                c.confirmerPaiement("prestataire", maintenant);
                evenements.save(new EvenementSortant(EvenementSortant.Type.COMMANDE_PAYEE, c.getId(), maintenant));
                journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "CONFIRME", String.valueOf(c.getId()),
                        "paiement confirmé pour la commande #" + c.getId() + " (" + fcfa(c.getTotal()) + ")");
            } else if (c.paiementRecuApresAnnulation()) {
                journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "TARDIF", String.valueOf(c.getId()),
                        "paiement reçu après l'annulation de la commande #" + c.getId()
                                + " — remboursement de " + fcfa(c.getTotal()) + " à signaler");
            }
        } else if (n.statut() == StatutPaiement.ECHOUE && c.getStatut() == StatutCommande.CREEE) {
            c.echecPaiement("prestataire", maintenant);
            journal.enregistrerEnTantQue("prestataire", Domaine.PAIEMENT, "ECHOUE", String.valueOf(c.getId()),
                    "paiement refusé : commande #" + c.getId() + " annulée");
        }
        return Resultat.TRAITEE;
    }

    /**
     * Toutes les minutes : les commandes encore en attente de paiement au-dela
     * du delai sont annulees (la cuisine ne les a jamais vues).
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public int expirer() {
        Instant maintenant = Instant.now(horloge);
        List<Long> ids = commandes.enAttenteDePaiementAvant(maintenant.minus(delaiExpiration), PageRequest.of(0, 200));
        int annulees = 0;
        for (Long id : ids) {
            Commande c = commandes.verrouiller(id).orElse(null);
            if (c != null && c.getStatut() == StatutCommande.CREEE) {
                c.echecPaiement("systeme", maintenant);
                journal.enregistrerEnTantQue("systeme", Domaine.PAIEMENT, "EXPIRE", String.valueOf(id), "commande #" + id
                        + " annulée : paiement non reçu sous " + delaiExpiration.toMinutes() + " min");
                annulees++;
            }
        }
        return annulees;
    }
}
