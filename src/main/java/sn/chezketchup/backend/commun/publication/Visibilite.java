package sn.chezketchup.backend.commun.publication;

import org.springframework.security.access.AccessDeniedException;

import sn.chezketchup.backend.commun.securite.UtilisateurCourant;

/**
 * Qui voit quoi dans le contenu editorial :
 * - visiteur (App Client) et staff hors Gerant : uniquement ACTIF. Demander
 *   explicitement BROUILLON ou ARCHIVE est refuse (403) : jamais de fuite
 *   d'un brouillon par simple parametre d'URL ;
 * - Gerant : tout, ou le statut demande.
 */
public final class Visibilite {

    private Visibilite() {
    }

    /** @return le statut a filtrer, ou null pour "tous les statuts" (Gerant sans filtre). */
    public static StatutPublication statutAFiltrer(StatutPublication demande) {
        if (UtilisateurCourant.estGerant()) {
            return demande;
        }
        if (demande != null && demande != StatutPublication.ACTIF) {
            throw new AccessDeniedException("Seul le contenu publie est consultable sans droits Gerant.");
        }
        return StatutPublication.ACTIF;
    }

    /** Vue publique (mise en cache, cache HTTP) : tout le monde sauf le Gerant. */
    public static boolean estVuePublique() {
        return !UtilisateurCourant.estGerant();
    }
}
