package sn.chezketchup.backend.commandes.domaine;

import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.security.Role;

/**
 * Transition hors de la table pour ce role / ce mode : HTTP 403 (contrat :
 * "Role insuffisant ou transition interdite").
 */
public class TransitionInterditeException extends RuntimeException {

    public TransitionInterditeException(StatutCommande de, StatutCommande vers, Role role) {
        super("Passage de " + de + " a " + vers + " non autorise pour le role " + role + ".");
    }
}
