package sn.chezketchup.backend.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * Refuse de demarrer en profil prod avec une configuration dangereuse :
 * secret JWT par defaut (connu de tous, puisqu'il est dans le depot) ou trop
 * court, comptes de demonstration actives, CORS ouvert a tous, donnees de
 * demonstration (db/demo) chargees par Flyway. Mieux vaut
 * un conteneur qui ne demarre pas qu'une API en production qu'on peut
 * contourner en forgeant un jeton.
 */
@Configuration
@Profile("prod")
public class VerificationConfigurationProd {

    static final String SECRET_JWT_DE_DEVELOPPEMENT =
            "changez-moi-avec-une-chaine-longue-et-aleatoire-en-production";
    static final int LONGUEUR_MIN_SECRET_OCTETS = 32;

    private final String secretJwt;
    private final boolean seedActive;
    private final String originesCors;
    private final String emplacementsFlyway;
    private final String prestatairePaiement;

    public VerificationConfigurationProd(
            @Value("${app.jwt.secret}") String secretJwt,
            @Value("${app.auth.seed.enabled:false}") boolean seedActive,
            @Value("${app.cors.allowed-origins:}") String originesCors,
            @Value("${spring.flyway.locations:}") String emplacementsFlyway,
            @Value("${app.paiement.prestataire:simule}") String prestatairePaiement
    ) {
        this.secretJwt = secretJwt;
        this.seedActive = seedActive;
        this.originesCors = originesCors;
        this.emplacementsFlyway = emplacementsFlyway;
        this.prestatairePaiement = prestatairePaiement;
    }

    @PostConstruct
    void verifier() {
        List<String> problemes = problemes(secretJwt, seedActive, originesCors);
        if (emplacementsFlyway != null && emplacementsFlyway.contains("db/demo")) {
            problemes.add("spring.flyway.locations ne doit pas inclure db/demo (donnees de demonstration)");
        }
        if ("simule".equalsIgnoreCase(prestatairePaiement)) {
            problemes.add("APP_PAIEMENT_PRESTATAIRE=simule interdit en production (PAIEMENT SIMULE : commandes confirmees sans argent reel)");
        }
        if (!problemes.isEmpty()) {
            throw new IllegalStateException(
                    "Configuration de production refusee : " + String.join(" ; ", problemes));
        }
    }

    /** Regles isolees pour etre testees sans demarrer Spring. */
    static List<String> problemes(String secretJwt, boolean seedActive, String originesCors) {
        List<String> problemes = new ArrayList<>();
        if (secretJwt == null || secretJwt.isBlank() || SECRET_JWT_DE_DEVELOPPEMENT.equals(secretJwt)) {
            problemes.add("APP_JWT_SECRET doit etre fourni (le secret par defaut du depot est interdit)");
        } else if (secretJwt.getBytes(StandardCharsets.UTF_8).length < LONGUEUR_MIN_SECRET_OCTETS) {
            problemes.add("APP_JWT_SECRET doit faire au moins " + LONGUEUR_MIN_SECRET_OCTETS + " octets");
        }
        if (seedActive) {
            problemes.add("APP_AUTH_SEED_ENABLED doit etre false (comptes de demonstration)");
        }
        if (originesCors != null && originesCors.contains("*")) {
            problemes.add("APP_CORS_ALLOWED_ORIGINS ne doit contenir aucun joker (*)");
        }
        return problemes;
    }
}
