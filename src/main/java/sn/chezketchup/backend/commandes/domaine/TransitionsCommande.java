package sn.chezketchup.backend.commandes.domaine;

import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.ANNULEE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.EN_LIVRAISON;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.EN_PREPARATION;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.LIVREE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.PAYEE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.PRETE;
import static sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.RETIREE;
import static sn.chezketchup.backend.security.Role.CUISINE;
import static sn.chezketchup.backend.security.Role.LIVREUR;
import static sn.chezketchup.backend.security.Role.VENDEUR;

import java.util.List;
import java.util.Set;

import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.security.Role;

/**
 * Table des transitions autorisees, IDENTIQUE a Manager_RestoApp
 * src/auth/transitions.js et au contrat (PATCH /commandes/{id}/statut).
 * Le serveur la revalide toujours : un front modifie ne peut pas la
 * contourner.
 *
 * | De             | Vers           | Role(s)            | Mode      |
 * |----------------|----------------|--------------------|-----------|
 * | PAYEE          | EN_PREPARATION | CUISINE            | -         |
 * | EN_PREPARATION | PRETE          | CUISINE            | -         |
 * | PRETE          | EN_LIVRAISON   | CUISINE ou LIVREUR | LIVRAISON |
 * | EN_LIVRAISON   | LIVREE         | LIVREUR            | LIVRAISON |
 * | PRETE          | RETIREE        | VENDEUR            | RETRAIT   |
 * | PAYEE          | ANNULEE        | CUISINE            | -         |
 */
public final class TransitionsCommande {

    public record Transition(StatutCommande de, StatutCommande vers, Set<Role> roles, ModeCommande mode) {

        boolean sApplique(StatutCommande depuis, StatutCommande cible, Role role, ModeCommande modeCommande) {
            return de == depuis && vers == cible && roles.contains(role) && (mode == null || mode == modeCommande);
        }
    }

    public static final List<Transition> TABLE = List.of(
            new Transition(PAYEE, EN_PREPARATION, Set.of(CUISINE), null),
            new Transition(EN_PREPARATION, PRETE, Set.of(CUISINE), null),
            new Transition(PRETE, EN_LIVRAISON, Set.of(CUISINE, LIVREUR), ModeCommande.LIVRAISON),
            new Transition(EN_LIVRAISON, LIVREE, Set.of(LIVREUR), ModeCommande.LIVRAISON),
            new Transition(PRETE, RETIREE, Set.of(VENDEUR), ModeCommande.RETRAIT),
            new Transition(PAYEE, ANNULEE, Set.of(CUISINE), null));

    private TransitionsCommande() {
    }

    public static boolean autorisee(StatutCommande depuis, StatutCommande cible, Role role, ModeCommande mode) {
        return TABLE.stream().anyMatch(t -> t.sApplique(depuis, cible, role, mode));
    }
}
