package sn.chezketchup.backend.paiements.infrastructure;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.paiements.application.PaymentProvider;

/**
 * PAIEMENT SIMULE : confirme immediatement tout paiement, sans argent reel.
 * Sert au developpement et a la demonstration tant que le prestataire n'est
 * pas choisi. Jamais actif en profil prod (absent du profil, et
 * VerificationConfigurationProd refuse app.paiement.prestataire=simule).
 *
 * C'est le SERVEUR qui confirme le paiement, jamais le client : le statut
 * PAYEE envoye aujourd'hui par l'App Client est ignore.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.paiement.prestataire", havingValue = "simule", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPaymentProvider.class);

    @Override
    public String code() {
        return "simule";
    }

    @Override
    public boolean estSimule() {
        return true;
    }

    @Override
    public ResultatInitiation initier(DemandePaiement demande) {
        String reference = "SIMULE-" + UUID.randomUUID();
        LOG.warn("PAIEMENT SIMULE : commande #{} confirmee sans paiement reel ({} F CFA, {})",
                demande.commandeId(), demande.montant(), demande.moyen().code());
        return new ResultatInitiation(reference, null, StatutPaiement.PAYE);
    }
}
