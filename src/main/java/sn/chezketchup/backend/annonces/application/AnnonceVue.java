package sn.chezketchup.backend.annonces.application;

import java.time.Instant;
import java.util.List;

import sn.chezketchup.backend.annonces.domaine.Annonce;
import sn.chezketchup.backend.annonces.domaine.AnnonceSlide;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Representation API (contrat : Annonce, AnnonceSlide). */
public record AnnonceVue(
        String id,
        String titre,
        List<SlideVue> slides,
        StatutPublication statut,
        Instant publishedAt,
        long version,
        Instant updatedAt) {

    public record SlideVue(String id, String titre, String message, String imageUrl) {

        static SlideVue de(AnnonceSlide s) {
            return new SlideVue(String.valueOf(s.getId()), s.getTitre(), s.getMessage(), s.getImageUrl());
        }
    }

    public static AnnonceVue de(Annonce a) {
        return new AnnonceVue(String.valueOf(a.getId()), a.getTitre(),
                a.getSlides().stream().map(SlideVue::de).toList(),
                a.getStatut(), a.getPublieeLe(), a.getVersion(), a.getModifieLe());
    }
}
