package sn.chezketchup.backend.payment;

/**
 * Abstraction pour les moyens de paiement en ligne (Wave, Orange Money, carte...).
 *
 * Objectif : demarrer avec un agregateur (ex. PayDunya) pour aller vite, puis
 * pouvoir brancher une integration directe plus tard sans reecrire le reste
 * de l'application (voir decision dans les notes de brainstorming du projet).
 *
 * A implementer : ex. PayDunyaPaymentProvider, plus tard WavePaymentProvider...
 */
public interface PaymentProvider {

    /**
     * Initie un paiement pour une commande et renvoie une reference/URL de paiement.
     */
    PaymentInitResult initPayment(PaymentRequest request);

    /**
     * Verifie le statut d'un paiement (ex. via webhook ou polling).
     */
    PaymentStatus checkStatus(String paymentReference);
}
