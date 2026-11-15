package sn.chezketchup.backend.tempsreel;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import sn.chezketchup.backend.commun.diffusion.ChangementPublic;

/**
 * Temps reel de l'App Client (sans compte) : signale qu'une donnee PUBLIQUE
 * a change (catalogue, rupture, plats du jour, annonces, horaires, zones).
 * Le message ne contient que le sujet ; l'App Client recharge la liste
 * concernee par l'API publique habituelle (memes regles de visibilite et de
 * cache). Aucune donnee personnelle ne passe par ce flux.
 *
 * Envoye APRES validation de la transaction : un client ne recharge jamais
 * une donnee pas encore enregistree. En memoire (une seule instance du
 * backend) ; plusieurs instances : Redis pub/sub, comme FluxCommandes.
 */
@Component
public class FluxPublic implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(FluxPublic.class);

    /** Garde-fou memoire : au-dela, les nouveaux clients rechargent par eux-memes. */
    static final int ABONNES_MAX = 5_000;
    static final Duration DUREE_CONNEXION = Duration.ofMinutes(30);

    private final List<SseEmitter> abonnes = new CopyOnWriteArrayList<>();
    private volatile boolean actif;

    /** Nouvel abonne, ou null si la limite est atteinte (l'appelant repond 503). */
    public SseEmitter abonner() {
        if (abonnes.size() >= ABONNES_MAX) {
            return null;
        }
        SseEmitter e = new SseEmitter(DUREE_CONNEXION.toMillis());
        try {
            e.send(SseEmitter.event().comment("connecte").reconnectTime(5_000));
        } catch (IOException deconnecte) {
            e.completeWithError(deconnecte);
            return e;
        }
        abonnes.add(e);
        e.onCompletion(() -> abonnes.remove(e));
        e.onTimeout(() -> abonnes.remove(e));
        e.onError(x -> abonnes.remove(e));
        return e;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void surChangement(ChangementPublic changement) {
        diffuser(changement.sujet().nom());
    }

    void diffuser(String sujet) {
        for (SseEmitter e : abonnes) {
            try {
                e.send(SseEmitter.event().name("changement").data(Map.of("sujet", sujet), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException deconnecte) {
                abonnes.remove(e);
            }
        }
    }

    public int nombreAbonnes() {
        return abonnes.size();
    }

    @Scheduled(fixedRate = 25_000)
    void signalDeVie() {
        for (SseEmitter e : abonnes) {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException deconnecte) {
                abonnes.remove(e);
            }
        }
        if (!abonnes.isEmpty()) {
            LOG.debug("{} App Client connectee(s) au temps reel", abonnes.size());
        }
    }

    @Override
    public void start() {
        actif = true;
    }

    @Override
    public void stop() {
        actif = false;
        for (SseEmitter e : abonnes) {
            try {
                e.complete();
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
}
