package sn.chezketchup.backend.commun.limitation;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limitation de debit par adresse IP (fenetre d'une minute) sur les routes
 * publiques exposees aux abus : creation de commande, suivi sans jeton
 * (enumeration de numeros de telephone), connexion (essais de mots de passe).
 * Au-dela : 429 {code: TROP_DE_REQUETES} + Retry-After.
 *
 * L'IP est la VRAIE IP du client : derriere le relais nginx des fronts,
 * Tomcat la lit dans X-Forwarded-For (server.forward-headers-strategy=native),
 * en ne faisant confiance qu'aux proxys des reseaux prives.
 *
 * Compteurs en memoire : suffisant pour une instance. Plusieurs instances :
 * compteurs partages (Redis) - voir "Reste a faire", point 10.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimitationDebitFilter extends OncePerRequestFilter {

    private final Map<String, Fenetre> fenetres = new ConcurrentHashMap<>();
    private final Clock horloge;
    private final int commandesParMinute;
    private final int suiviParMinute;
    private final int connexionsParMinute;

    public LimitationDebitFilter(Clock horloge,
                                 @Value("${app.limitation.commandes-par-minute:10}") int commandesParMinute,
                                 @Value("${app.limitation.suivi-par-minute:30}") int suiviParMinute,
                                 @Value("${app.limitation.connexions-par-minute:10}") int connexionsParMinute) {
        this.horloge = horloge;
        this.commandesParMinute = commandesParMinute;
        this.suiviParMinute = suiviParMinute;
        this.connexionsParMinute = connexionsParMinute;
    }

    private static final class Fenetre {
        long minute;
        int compte;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
        String methode = requete.getMethod();
        String regle = null;
        int limite = 0;
        if ("POST".equals(methode) && "/commandes".equals(chemin)) {
            regle = "commandes";
            limite = commandesParMinute;
        } else if ("POST".equals(methode) && "/auth/login".equals(chemin)) {
            regle = "connexion";
            limite = connexionsParMinute;
        } else if ("GET".equals(methode) && chemin.startsWith("/commandes") && requete.getHeader("Authorization") == null) {
            regle = "suivi";
            limite = suiviParMinute;
        }
        if (regle != null && !autoriser(regle + "|" + requete.getRemoteAddr(), limite)) {
            long minute = horloge.millis() / 60_000;
            long attente = Math.max(1, (minute + 1) * 60 - horloge.millis() / 1000);
            reponse.setStatus(429);
            reponse.setHeader("Retry-After", String.valueOf(attente));
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("{\"code\":\"TROP_DE_REQUETES\",\"message\":"
                    + "\"Trop de requetes. Reessayez dans une minute.\",\"field\":null}");
            return;
        }
        chaine.doFilter(requete, reponse);
    }

    boolean autoriser(String cle, int limite) {
        long minute = horloge.millis() / 60_000;
        Fenetre f = fenetres.computeIfAbsent(cle, k -> new Fenetre());
        synchronized (f) {
            if (f.minute != minute) {
                f.minute = minute;
                f.compte = 0;
            }
            f.compte++;
            return f.compte <= limite;
        }
    }

    /** Oubli des fenetres expirees (la memoire ne grossit pas avec le nombre d'IP vues). */
    @Scheduled(fixedDelay = 300_000)
    void nettoyer() {
        long minute = horloge.millis() / 60_000;
        fenetres.entrySet().removeIf(e -> e.getValue().minute < minute - 1);
    }
}
