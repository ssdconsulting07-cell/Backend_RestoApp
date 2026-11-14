package sn.chezketchup.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class VerificationConfigurationProdTest {

    private static final String SECRET_SOLIDE = "k3Y9-uN-secret-de-production-tres-long-et-aleatoire-42";

    @Test
    void accepteUneConfigurationSaine() {
        assertThat(VerificationConfigurationProd.problemes(SECRET_SOLIDE, false, "https://app.senyummies.sn"))
                .isEmpty();
    }

    @Test
    void refuseLeSecretParDefautDuDepot() {
        assertThat(VerificationConfigurationProd.problemes(
                VerificationConfigurationProd.SECRET_JWT_DE_DEVELOPPEMENT, false, ""))
                .singleElement().asString().contains("APP_JWT_SECRET");
    }

    @Test
    void refuseUnSecretAbsentOuTropCourt() {
        assertThat(VerificationConfigurationProd.problemes("", false, "")).hasSize(1);
        assertThat(VerificationConfigurationProd.problemes("court", false, ""))
                .singleElement().asString().contains("32 octets");
    }

    @Test
    void refuseLesComptesDeDemoEtLeJokerCors() {
        assertThat(VerificationConfigurationProd.problemes(SECRET_SOLIDE, true, "*")).hasSize(2);
    }

    @Test
    void refuseLesDonneesDeDemoEnProd() {
        VerificationConfigurationProd verification = new VerificationConfigurationProd(
                SECRET_SOLIDE, false, "", "classpath:db/migration,classpath:db/demo", "paydunya", new MockEnvironment());
        assertThatThrownBy(verification::verifier).hasMessageContaining("db/demo");
    }

    @Test
    void refuseLePaiementSimuleEnProd() {
        VerificationConfigurationProd verification = new VerificationConfigurationProd(
                SECRET_SOLIDE, false, "", "classpath:db/migration", "simule", new MockEnvironment());
        assertThatThrownBy(verification::verifier).hasMessageContaining("PAIEMENT SIMULE");
    }

    @Test
    void empecheLeDemarrage() {
        VerificationConfigurationProd verification =
                new VerificationConfigurationProd(VerificationConfigurationProd.SECRET_JWT_DE_DEVELOPPEMENT, true, "", "classpath:db/migration", "paydunya", new MockEnvironment());
        assertThatThrownBy(verification::verifier)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration de production refusee");
    }
    @Test
    void refuseLeProfilDeTestsDeChargeEnProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "loadtest");
        VerificationConfigurationProd verification = new VerificationConfigurationProd(
                SECRET_SOLIDE, false, "", "classpath:db/migration", "paydunya", env);
        assertThatThrownBy(verification::verifier).hasMessageContaining("loadtest");
    }

    @Test
    void refuseActuatorEnProd() {
        MockEnvironment expose = new MockEnvironment().withProperty("management.endpoints.web.exposure.include", "prometheus");
        MockEnvironment acces = new MockEnvironment().withProperty("management.endpoints.access.default", "read-only");
        MockEnvironment port = new MockEnvironment().withProperty("management.server.port", "9090");
        for (MockEnvironment env : new MockEnvironment[] {expose, acces, port}) {
            VerificationConfigurationProd verification = new VerificationConfigurationProd(
                    SECRET_SOLIDE, false, "", "classpath:db/migration", "paydunya", env);
            assertThatThrownBy(verification::verifier).hasMessageContaining("Actuator");
        }
    }

    @Test
    void accepteLaProdSansActuatorNiLoadtest() {
        MockEnvironment env = new MockEnvironment().withProperty("management.endpoints.access.default", "none");
        env.setActiveProfiles("prod");
        new VerificationConfigurationProd(SECRET_SOLIDE, false, "", "classpath:db/migration", "paydunya", env).verifier();
    }
}
