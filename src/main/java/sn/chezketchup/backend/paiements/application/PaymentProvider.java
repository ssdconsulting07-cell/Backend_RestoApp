package sn.chezketchup.backend.paiements.application;

import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;

/**
 * Prestataire de paiement en ligne (Wave, Orange Money, Mixx by Yas, carte).
 *
 * Le choix du prestataire (PayDunya ou API directes) est en attente de la
 * decision du PDG. Aujourd'hui : {@code SimulatedPaymentProvider}
 * (developpement uniquement, refuse en production). Brancher le vrai
 * prestataire = ecrire une implementation de cette interface et la
 * selectionner par app.paiement.prestataire (voir "Reste a faire").
 *
 * Aucune donnee de carte ne transite par SenYummies : la saisie se fait chez
 * le prestataire (page ou module heberge), nous ne recevons qu'un statut.
 */
public interface PaymentProvider {

    /** Code stocke avec chaque paiement (ex. "simule", "paydunya"). */
    String code();

    /** Vrai pour le simulateur : jamais d'argent reel. */
    boolean estSimule();

    ResultatInitiation initier(DemandePaiement demande);

    /** Montant en F CFA entiers ; telephone du client pour le paiement mobile. */
    record DemandePaiement(long commandeId, long montant, MoyenPaiement moyen, String telephone) {
    }

    /**
     * @param reference      reference du paiement chez le prestataire
     * @param redirectUrl    page de paiement a ouvrir par le client (null si aucune)
     * @param statutImmediat statut connu tout de suite (simulateur), sinon null :
     *                       le statut arrivera par le webhook du prestataire
     */
    record ResultatInitiation(String reference, String redirectUrl, StatutPaiement statutImmediat) {
    }
}
