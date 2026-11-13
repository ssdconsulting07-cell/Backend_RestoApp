package sn.chezketchup.backend.paiements.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.commandes.domaine.StatutPaiement;
import sn.chezketchup.backend.paiements.application.PaymentProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PAIEMENT SIMULE : aucun argent reel. Sert au developpement et a la
 * demonstration tant que le prestataire n'est pas choisi. Jamais actif en
 * profil prod (absent du profil, et VerificationConfigurationProd refuse
 * app.paiement.prestataire=simule).
 *
 * Deux modes (app.paiement.simule.mode) :
 * - immediat (defaut) : paiement confirme a l'initiation ; l'App Client
 *   actuelle fonctionne sans changement ;
 * - webhook : la commande reste en attente jusqu'a une notification signee
 *   sur /paiements/webhook, comme avec un vrai prestataire.
 *
 * C'est aussi l'IMPLEMENTATION DE REFERENCE pour le vrai prestataire :
 * signature HMAC-SHA256 du corps brut, horodatage contre le rejeu,
 * identifiant d'evenement pour l'idempotence.
 *
 * Format du webhook simule : en-tetes X-Horodatage (secondes epoch) et
 * X-Signature = hex(HMAC-SHA256(secret, horodatage + "." + corps)) ;
 * corps {"evenementId","reference","statut":"paye|echoue","montant"}.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.paiement.prestataire", havingValue = "simule", matchIfMissing = true)
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPaymentProvider.class);
    static final long TOLERANCE_SECONDES = 300;

    private final String mode;
    private final String secretWebhook;
    private final ObjectMapper json;
    private final Clock horloge;

    public SimulatedPaymentProvider(@Value("${app.paiement.simule.mode:immediat}") String mode,
                                    @Value("${app.paiement.simule.secret-webhook}") String secretWebhook,
                                    ObjectMapper json, Clock horloge) {
        this.mode = mode;
        this.secretWebhook = secretWebhook;
        this.json = json;
        this.horloge = horloge;
    }

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
        if ("webhook".equals(mode)) {
            LOG.warn("PAIEMENT SIMULE : commande #{} en attente d'une notification simulee (reference {})",
                    demande.commandeId(), reference);
            return new ResultatInitiation(reference, null, null);
        }
        LOG.warn("PAIEMENT SIMULE : commande #{} confirmee sans paiement reel ({} F CFA, {})",
                demande.commandeId(), demande.montant(), demande.moyen().code());
        return new ResultatInitiation(reference, null, StatutPaiement.PAYE);
    }

    @Override
    public NotificationPaiement lireNotification(byte[] corps, Map<String, String> entetes) {
        String horodatage = entete(entetes, "X-Horodatage");
        String signature = entete(entetes, "X-Signature");
        if (horodatage == null || signature == null || !horodatage.matches("\\d{1,12}")) {
            throw new SignatureInvalideException("Signature ou horodatage manquant.");
        }
        long ecart = Math.abs(horloge.millis() / 1000 - Long.parseLong(horodatage));
        if (ecart > TOLERANCE_SECONDES) {
            throw new SignatureInvalideException("Notification trop ancienne (rejeu ?).");
        }
        byte[] attendue = signer(secretWebhook, horodatage, corps).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(attendue, signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new SignatureInvalideException("Signature invalide.");
        }
        JsonNode n = json.readTree(corps);
        return new NotificationPaiement(n.get("evenementId").asString(), n.get("reference").asString(),
                StatutPaiement.depuisCode(n.get("statut").asString()), n.get("montant").asLong());
    }

    /** Signature HMAC-SHA256 en hexadecimal (utilisee aussi par les tests et la documentation). */
    public static String signer(String secret, String horodatage, byte[] corps) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((horodatage + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(corps));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String entete(Map<String, String> entetes, String nom) {
        return entetes.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(nom))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
