package sn.chezketchup.backend.commandes.application;

import java.util.EnumSet;
import java.util.Set;

import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.security.Role;

/**
 * Chaque role ne voit que sa file de travail :
 * - CUISINE : toutes les commandes payees (elle prepare tout) ;
 * - LIVREUR : les livraisons pretes, en route et livrees ;
 * - VENDEUR : les commandes a emporter (comptoir et App Client) ;
 * - MANAGER, GERANT : tout (lecture, statistiques, audit).
 * Une commande non payee (CREEE) n'apparait dans aucune file operationnelle.
 */
public final class VisibiliteCommandes {

    private VisibiliteCommandes() {
    }

    public static ModeCommande modeDe(Role role) {
        return switch (role) {
            case LIVREUR -> ModeCommande.LIVRAISON;
            case VENDEUR -> ModeCommande.RETRAIT;
            default -> null;
        };
    }

    public static Set<StatutCommande> statutsDe(Role role) {
        return switch (role) {
            case LIVREUR -> EnumSet.of(StatutCommande.PRETE, StatutCommande.EN_LIVRAISON, StatutCommande.LIVREE);
            case CUISINE, VENDEUR -> EnumSet.complementOf(EnumSet.of(StatutCommande.CREEE));
            case MANAGER, GERANT -> EnumSet.allOf(StatutCommande.class);
        };
    }

    public static boolean voit(Role role, Commande c) {
        ModeCommande mode = modeDe(role);
        return (mode == null || mode == c.getMode()) && statutsDe(role).contains(c.getStatut());
    }
}
