package sn.chezketchup.backend.catalogue.application;

import java.time.Instant;

import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Representation API d'un produit (contrat : Produit). Prix en F CFA entiers. */
public record ProduitVue(
        String id,
        String nom,
        String description,
        long prix,
        String categorieId,
        String photoUrl,
        StatutPublication statut,
        Disponibilite disponibilite,
        boolean tendance,
        long version,
        Instant updatedAt) {

    public static ProduitVue de(Produit p) {
        return new ProduitVue(String.valueOf(p.getId()), p.getNom(), p.getDescription(), p.getPrix(),
                String.valueOf(p.getCategorieId()), p.getPhotoUrl(), p.getStatut(), p.getDisponibilite(),
                p.isTendance(), p.getVersion(), p.getModifieLe());
    }
}
