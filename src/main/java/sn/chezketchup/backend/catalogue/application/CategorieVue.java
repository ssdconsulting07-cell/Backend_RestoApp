package sn.chezketchup.backend.catalogue.application;

import java.time.Instant;

import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Representation API d'une categorie (contrat : Categorie). */
public record CategorieVue(
        String id,
        String nom,
        StatutPublication statut,
        boolean accompagnementPropose,
        long version,
        Instant updatedAt) {

    public static CategorieVue de(Categorie c) {
        return new CategorieVue(String.valueOf(c.getId()), c.getNom(), c.getStatut(),
                c.isAccompagnementPropose(), c.getVersion(), c.getModifieLe());
    }
}
