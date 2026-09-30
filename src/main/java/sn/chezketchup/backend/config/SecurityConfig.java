package sn.chezketchup.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import sn.chezketchup.backend.auth.StaffUserRepository;
import sn.chezketchup.backend.web.ApiError;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import sn.chezketchup.backend.security.JwtAuthenticationFilter;

import java.io.IOException;
import java.util.List;

/**
 * Regles de base, a affiner quand les vrais controleurs seront ecrits :
 * - L'App Client (commande invite) n'authentifie jamais ses utilisateurs :
 *   menu, creation de commande et paiement restent publics.
 * - SenYummies Manager authentifie tout le reste via JWT (voir JwtAuthenticationFilter).
 *   Le detail des roles par route (Cuisine, Gerant, Manager, Livreur) se fait
 *   ensuite via @PreAuthorize sur chaque controleur (EnableMethodSecurity).
 *
 * Gestion des erreurs d'authentification/autorisation : sans configuration
 * explicite, Spring Security renvoie par defaut un 403 vide (Http403ForbiddenEntryPoint)
 * des qu'une route protegee est appelee sans token, avec un token invalide ou
 * expire. Ca casse deux choses cote SenYummies Manager : le contrat documente
 * un 401 sur ces routes (voir openapi.yaml), et api/client.js ne declenche la
 * deconnexion automatique (SESSION_EXPIRED_EVENT) que sur un 401 exact. Les
 * deux beans ci-dessous renvoient donc explicitement le format d'erreur
 * standard ({@link ApiError}) avec le bon code HTTP : 401 quand la requete
 * n'est pas authentifiee (pas de token, token invalide ou expire), 403 quand
 * elle l'est mais que le role ne suffit pas.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final StaffUserRepository staffUserRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            StaffUserRepository staffUserRepository,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.staffUserRepository = staffUserRepository;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/produits/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/commandes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/commandes/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/paiements/**").permitAll()
                        // Documentation API (Swagger UI + spec OpenAPI generee par springdoc) : consultee
                        // sans authentification par les equipes App Client et SenYummies Manager (voir
                        // decision du 30/09 : le contrat vit ici, les 2 autres equipes le lisent via Swagger).
                        .requestMatchers(
                                HttpMethod.GET,
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> staffUserRepository.findByUsernameIgnoreCase(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().name())
                        .disabled(!user.isActive())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Staff user not found"));
    }

    // 401 : requete non authentifiee (pas de token, token invalide ou expire).
    // C'est ce statut precis que api/client.js (SenYummies Manager) attend pour
    // effacer le token local et rediriger vers /login (voir SESSION_EXPIRED_EVENT).
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeApiError(
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentification requise. Token manquant, invalide ou expire."
        );
    }

    // 403 : requete authentifiee mais role insuffisant pour la ressource demandee.
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeApiError(
                response,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Acces refuse : votre role ne permet pas cette action."
        );
    }

    private void writeApiError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(code, message)));
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
