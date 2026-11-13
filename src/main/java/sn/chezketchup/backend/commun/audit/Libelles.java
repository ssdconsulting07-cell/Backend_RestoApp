package sn.chezketchup.backend.commun.audit;

import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Formulations du journal d'audit, identiques a celles du Manager. */
public final class Libelles {

    private Libelles() {
    }

    /** 3500 -> "3 500 F CFA" (espace insecable fine, comme le Manager). */
    public static String fcfa(long montant) {
        return String.format(java.util.Locale.FRANCE, "%,d", montant).replace(' ', ' ').replace(' ', ' ')
                + " F CFA";
    }

    public static String guillemets(String texte) {
        return "« " + texte + " »";
    }

    /** "a publie", "a depublie", "a restaure", "a archive". */
    public static String verbeStatut(StatutPublication avant, StatutPublication apres) {
        return switch (apres) {
            case ACTIF -> "a publié";
            case ARCHIVE -> "a archivé";
            case BROUILLON -> avant == StatutPublication.ARCHIVE ? "a restauré" : "a dépublié";
        };
    }
}
