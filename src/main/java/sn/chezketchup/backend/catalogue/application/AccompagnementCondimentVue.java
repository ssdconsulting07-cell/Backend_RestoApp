package sn.chezketchup.backend.catalogue.application;

import java.time.Instant;

import sn.chezketchup.backend.catalogue.domaine.AccompagnementCondiment;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Representation API (contrat : AccompagnementCondiment). Prix 0 = offert. */
public record AccompagnementCondimentVue(
        String id,
        String nom,
        long prix,
        TypeAccompagnement type,
        StatutPublication statut,
        long version,
        Instant updatedAt) {

    public static AccompagnementCondimentVue de(AccompagnementCondiment a) {
        return new AccompagnementCondimentVue(String.valueOf(a.getId()), a.getNom(), a.getPrix(), a.getType(),
                a.getStatut(), a.getVersion(), a.getModifieLe());
    }
}
