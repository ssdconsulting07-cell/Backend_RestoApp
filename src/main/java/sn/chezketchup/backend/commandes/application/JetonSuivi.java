package sn.chezketchup.backend.commandes.application;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import sn.chezketchup.backend.commun.idempotence.Idempotence;

/**
 * Jeton de suivi d'une commande APP : secret aleatoire (256 bits) remis une
 * seule fois au client a la creation, garde sur son telephone. Seule son
 * empreinte SHA-256 est stockee : une fuite de la base ne donne pas les
 * jetons. Sans lui, un numero de commande (sequentiel, donc devinable) ne
 * donne acces a rien.
 */
public final class JetonSuivi {

    private static final SecureRandom ALEA = new SecureRandom();

    private JetonSuivi() {
    }

    public static String generer() {
        byte[] octets = new byte[32];
        ALEA.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    public static String empreinte(String jeton) {
        return Idempotence.empreinte(jeton);
    }

    /** Comparaison a temps constant (pas d'indice par la duree de reponse). */
    public static boolean correspond(String jeton, String empreinteStockee) {
        if (jeton == null || jeton.isBlank() || empreinteStockee == null) {
            return false;
        }
        return MessageDigest.isEqual(empreinte(jeton.trim()).getBytes(), empreinteStockee.getBytes());
    }
}
