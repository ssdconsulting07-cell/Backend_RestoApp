package sn.chezketchup.backend.commandes.application;

import java.util.Optional;

/** Numeros senegalais : "77 123 45 67", "771234567", "+221 77..." -> "+221771234567". Meme regle que l'App Client. */
public final class Telephone {

    private Telephone() {
    }

    public static Optional<String> normaliser(String saisie) {
        if (saisie == null) {
            return Optional.empty();
        }
        String chiffres = saisie.replaceAll("[^0-9]", "");
        if (chiffres.startsWith("00221")) {
            chiffres = chiffres.substring(5);
        } else if (chiffres.startsWith("221") && chiffres.length() == 12) {
            chiffres = chiffres.substring(3);
        }
        return chiffres.matches("\\d{9}") ? Optional.of("+221" + chiffres) : Optional.empty();
    }
}
