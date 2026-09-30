package sn.chezketchup.backend.payment;

import java.math.BigDecimal;

/**
 * Donnees necessaires pour initier un paiement en ligne pour une commande.
 */
public record PaymentRequest(
        String orderReference,
        BigDecimal amount,
        String customerPhone
) {}
