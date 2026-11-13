package sn.chezketchup.backend.annonces.application;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import sn.chezketchup.backend.commun.validation.UrlImage;

/** Chaine saisie par le Gerant : 1 a 10 slides, remplacees en bloc a chaque modification. */
public record AnnonceSaisie(
        @NotBlank @Size(max = 80) String titre,
        @NotEmpty @Size(max = 10) List<@Valid Slide> slides,
        Long version) {

    public record Slide(
            @NotBlank @Size(max = 80) String titre,
            @Size(max = 300) String message,
            @UrlImage String imageUrl) {
    }
}
