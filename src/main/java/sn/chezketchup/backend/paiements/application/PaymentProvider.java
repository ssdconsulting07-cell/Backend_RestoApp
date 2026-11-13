package sn.chezketchup.backend.paiements.application;

import java.util.Map;

import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.domaine.StatutPaiement;

/**
 * Prestataire de paiement en ligne (Wave, Orange Money, Mixx by Yas, carte).
 *
 * Le choix du prestataire (PayDunya ou API directes) est en attente de la
 * decision du PDG. Aujourd'hui : {@code SimulatedPaymentProvider}
 * (developpement uniquement, refuse en production). Brancher le vrai
 * prestataire = ecrire UNE implementation de cette interface et la
 * selectionner par app.paiement.prestataire ; rien d'autre ne change
 * (voir le document "Reste a faire").
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

    /**
     * Lit et AUTHENTIFIE une notification (webhook) du prestataire.
     *
     * @throws SignatureInvalideException si la signature (ou l'horodatage)
     *         ne correspond pas : la notification est ignoree (401)
     */
    NotificationPaiement lireNotification(byte[] corps, Map<String, String> entetes);

    /** Montant en F CFA entiers ; telephone du client pour le paiement mobile. */
    record DemandePaiement(long commandeId, long montant, MoyenPaiement moyen, String telephone) {
    }

    /**
     * @param reference      reference du paiement chez le prestataire
     * @param redirectUrl    page de paiement a ouvrir par le client (null si aucune)
     * @param statutImmediat statut connu tout de suite (simulateur en mode
     *                       immediat), sinon null : il arrivera par le webhook
     */
    record ResultatInitiation(String reference, String redirectUrl, StatutPaiement statutImmediat) {
    }

    /**
     * Notification authentifiee.
     *
     * @param evenementId identifiant unique de la notification chez le
     *                    prestataire (sert a ne la traiter qu'une fois)
     */
    record NotificationPaiement(String evenementId, String reference, StatutPaiement statut, long montant) {
    }

    /** Signature absente ou fausse, ou notification trop ancienne (rejeu). */
    class SignatureInvalideException extends RuntimeException {

        public SignatureInvalideException(String message) {
            super(message);
        }
    }
}
