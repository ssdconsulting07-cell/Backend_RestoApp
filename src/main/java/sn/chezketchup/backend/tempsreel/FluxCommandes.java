package sn.chezketchup.backend.tempsreel;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import sn.chezketchup.backend.commandes.application.CommandeVue;
import sn.chezketchup.backend.commandes.application.VisibiliteCommandes;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.security.Role;

/**
 * Postes du staff connectes au flux temps reel (SSE). Chaque poste ne recoit
 * que les commandes de sa file (VisibiliteCommandes), avec la vue filtree
 * de son role (donnees personnelles comprises).
 *
 * En memoire : une seule instance du backend. Plusieurs instances : diffuser
 * via Redis (pub/sub) ; l'outbox reste la source (voir "Reste a faire").
 */
@Component
public class FluxCommandes implements SmartLifecycle {

    private volatile boolean actif;

    /**
     * Arret du serveur : les flux SSE sont fermes AVANT l'arret progressif de
     * Tomcat (phase plus haute = arrete en premier). Sinon Tomcat attend la
     * fin de ces requetes qui ne finissent jamais, et chaque redeploiement
     * prend 30 s de plus. Les postes se reconnectent seuls (Last-Event-ID).
     */
    @Override
    public void start() {
        actif = true;
    }

    @Override
    public void stop() {
        actif = false;
        for (Abonne a : abonnes) {
            try {
                a.emetteur().complete();
            } catch (RuntimeException dejaFerme) {
                // rien a faire
            }
        }
        abonnes.clear();
    }

    @Override
    public boolean isRunning() {
        return actif;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private static final Logger LOG = LoggerFactory.getLogger(FluxCommandes.class);

    record Abonne(SseEmitter emetteur, Role role) {
    }

    /** Charge d'un evenement : type + commande vue par le role de l'abonne. */
    public record Evenement(String type, CommandeVue commande) {
    }

    private final List<Abonne> abonnes = new CopyOnWriteArrayList<>();

    public void abonner(SseEmitter emetteur, Role role) {
        Abonne a = new Abonne(emetteur, role);
        abonnes.add(a);
        emetteur.onCompletion(() -> abonnes.remove(a));
        emetteur.onTimeout(() -> abonnes.remove(a));
        emetteur.onError(e -> abonnes.remove(a));
    }

    public int nombreAbonnes() {
        return abonnes.size();
    }

    public void diffuser(long idEvenement, String type, Commande commande) {
        for (Abonne a : abonnes) {
            if (VisibiliteCommandes.voit(a.role(), commande)) {
                envoyer(a, idEvenement, type, commande);
            }
        }
    }

    /** Envoi a un seul poste (rattrapage apres reconnexion). */
    public void envoyer(SseEmitter emetteur, Role role, long idEvenement, String type, Commande commande) {
        if (VisibiliteCommandes.voit(role, commande)) {
            envoyer(new Abonne(emetteur, role), idEvenement, type, commande);
        }
    }

    private void envoyer(Abonne a, long idEvenement, String type, Commande commande) {
        try {
            a.emetteur().send(SseEmitter.event()
                    .id(String.valueOf(idEvenement))
                    .name("commande")
                    .data(new Evenement(type, CommandeVue.pourStaff(commande, a.role())), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException deconnecte) {
            abonnes.remove(a);
        }
    }

    /**
     * Signal de vie toutes les 25 s : garde la connexion ouverte a travers les
     * proxys et detecte les postes deconnectes.
     */
    @Scheduled(fixedRate = 25_000)
    void signalDeVie() {
        for (Abonne a : abonnes) {
            try {
                a.emetteur().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException deconnecte) {
                abonnes.remove(a);
            }
        }
        if (!abonnes.isEmpty()) {
            LOG.debug("{} poste(s) connecte(s) au temps reel", abonnes.size());
        }
    }
}
