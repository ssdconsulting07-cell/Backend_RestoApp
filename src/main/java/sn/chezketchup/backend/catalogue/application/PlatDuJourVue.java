package sn.chezketchup.backend.catalogue.application;

import java.time.Instant;

import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.PlatDuJour;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Representation API d'un plat du jour (contrat : PlatDuJour). */
public record PlatDuJourVue(
        String id,
        String nom,
        String description,
        long prix,
        String photoUrl,
        JourSemaine jour,
        java.util.Set<JourSemaine> jours,
        boolean dejeuner,
        boolean diner,
        StatutPublication statut,
        long version,
        Instant updatedAt) {

    public static PlatDuJourVue de(PlatDuJour p) {
        return new PlatDuJourVue(String.valueOf(p.getId()), p.getNom(), p.getDescription(), p.getPrix(),
                p.getPhotoUrl(), p.getJour(), p.getJours(), p.isDejeuner(), p.isDiner(), p.getStatut(), p.getVersion(), p.getModifieLe());
    }
}
