package sn.chezketchup.backend.payment;

/**
 * Resultat du lancement d'un paiement : reference interne + lien a suivre par le client
 * (ex. lien de paiement PayDunya, ou reference a confirmer via l'app mobile Wave/OM).
 */
public record PaymentInitResult(
        String paymentReference,
        String redirectUrl
) {}
