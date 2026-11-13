package sn.chezketchup.backend.commun.idempotence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;

/**
 * En-tete Idempotency-Key : une requete renvoyee (coupure reseau, double
 * appui) avec la meme cle n'est traitee qu'une fois ; la premiere reponse
 * est rejouee a l'identique.
 *
 * 1. reserver() dans sa propre transaction : la cle unique en base sert de
 *    verrou entre deux envois simultanes ;
 * 2. la reponse est enregistree DANS la transaction du traitement
 *    (memoriser()) : commande et reponse ensemble, ou rien ;
 * 3. en cas d'echec du traitement, liberer() efface la cle pour permettre
 *    un nouvel essai.
 */
@Service
public class Idempotence {

    private static final Pattern CLE_VALIDE = Pattern.compile("^[A-Za-z0-9_\\-:.]{8,100}$");
    static final Duration CONSERVATION = Duration.ofHours(24);

    private final CleIdempotenceRepository cles;
    private final Clock horloge;

    public Idempotence(CleIdempotenceRepository cles, Clock horloge) {
        this.cles = cles;
        this.horloge = horloge;
    }

    /** @return la reponse deja produite pour cette cle, ou vide si la requete doit etre traitee. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
    public Optional<String> reserver(String cle, String empreinteRequete) {
        verifierFormat(cle);
        Optional<CleIdempotence> existante = cles.findById(cle);
        if (existante.isEmpty()) {
            try {
                cles.saveAndFlush(new CleIdempotence(cle, empreinteRequete, Instant.now(horloge)));
                return Optional.empty();
            } catch (DataIntegrityViolationException envoiSimultane) {
                throw enCours();
            }
        }
        CleIdempotence c = existante.get();
        if (!c.getEmpreinteRequete().equals(empreinteRequete)) {
            throw new ConflitException("IDEMPOTENCE_CONFLIT",
                    "Cette cle Idempotency-Key a deja servi pour une autre requete.");
        }
        if (c.getReponse() == null) {
            throw enCours();
        }
        return Optional.of(c.getReponse());
    }

    /** A appeler dans la transaction du traitement. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void memoriser(String cle, long commandeId, String reponse) {
        cles.findById(cle).ifPresent(c -> c.enregistrerReponse(commandeId, reponse));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void liberer(String cle) {
        cles.deleteById(cle);
    }

    /** Purge quotidienne des cles de plus de 24 h (elles contiennent la reponse, donc le jeton de suivi). */
    @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Dakar")
    @Transactional
    public void purger() {
        cles.purgerAvant(Instant.now(horloge).minus(CONSERVATION));
    }

    public static String empreinte(String contenu) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(contenu.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void verifierFormat(String cle) {
        if (!CLE_VALIDE.matcher(cle).matches()) {
            throw new RequeteInvalideException("IDEMPOTENCE_CLE_INVALIDE",
                    "Idempotency-Key : 8 a 100 caracteres (lettres, chiffres, - _ : .).", "Idempotency-Key");
        }
    }

    private static ConflitException enCours() {
        return new ConflitException("REQUETE_EN_COURS",
                "Cette requete est deja en cours de traitement. Reessayez dans quelques secondes.");
    }
}
