package sn.chezketchup.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Profil loadtest UNIQUEMENT : metriques (Actuator, Prometheus) lisibles sans
 * jeton sur le port de gestion (9090). Ce port n'est jamais relaye par nginx
 * et le compose de test ne le publie que sur 127.0.0.1. Les routes de l'API
 * (port 8080) gardent leur securite habituelle. Le profil prod refuse de
 * demarrer avec loadtest ou Actuator (VerificationConfigurationProd).
 */
@Configuration
@Profile("loadtest")
public class SecuriteMetriquesLoadtest {

    @Bean
    @Order(0)
    SecurityFilterChain metriquesLoadtest(HttpSecurity http,
                                          @Value("${management.server.port}") int portGestion) throws Exception {
        return http
                .securityMatcher(requete -> requete.getLocalPort() == portGestion)
                .authorizeHttpRequests(acces -> acces.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
