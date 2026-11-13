package sn.chezketchup.backend.commun.securite;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Qui fait la requete : membre du staff authentifie (jeton JWT) ou visiteur
 * anonyme (App Client). Lecture seule du contexte Spring Security.
 */
public final class UtilisateurCourant {

    private UtilisateurCourant() {
    }

    public static Optional<String> identifiant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth.getName());
    }

    public static boolean aLeRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    /** Role du membre du staff connecte (vide pour un visiteur de l'App Client). */
    public static Optional<sn.chezketchup.backend.security.Role> role() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .flatMap(nom -> java.util.Arrays.stream(sn.chezketchup.backend.security.Role.values())
                        .filter(r -> r.name().equals(nom)))
                .findFirst();
    }

    /** Le Gerant voit aussi les brouillons et archives ; tous les autres, ACTIF seulement. */
    public static boolean estGerant() {
        return aLeRole("GERANT");
    }
}
