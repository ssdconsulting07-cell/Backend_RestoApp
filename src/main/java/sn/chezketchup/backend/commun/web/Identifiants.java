package sn.chezketchup.backend.commun.web;

import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;

/**
 * Le contrat expose des identifiants en texte ; en base ce sont des entiers.
 * Conversion stricte, sans exception technique qui fuirait en 500.
 */
public final class Identifiants {

    private Identifiants() {
    }

    /** Identifiant de chemin (/produits/{id}) : invalide = ressource introuvable. */
    public static long chemin(String id, String ressource) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new RessourceIntrouvableException(ressource + " introuvable.");
        }
    }

    /** Identifiant dans un corps ou un parametre : invalide = 400 sur le champ. */
    public static Long champ(String id, String champ) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new RequeteInvalideException("IDENTIFIANT_INVALIDE", "Identifiant invalide.", champ);
        }
    }
}
