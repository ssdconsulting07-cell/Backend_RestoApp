package sn.chezketchup.backend.photos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.photos.application.TraitementPhoto;
import sn.chezketchup.backend.photos.application.TraitementPhoto.PhotoNettoyee;
import sn.chezketchup.backend.photos.domaine.FormatImage;

class TraitementPhotoTest {

    @Test
    void lesMetadonneesExifSontRetirees() {
        byte[] entree = ImagesDeTest.jpegAvecExifGps(800, 600);
        assertThat(ImagesDeTest.contient(entree, "GPSLatitude")).as("le fichier de test contient bien un EXIF").isTrue();

        PhotoNettoyee sortie = TraitementPhoto.nettoyer(entree);
        assertThat(ImagesDeTest.contient(sortie.contenu(), "Exif")).isFalse();
        assertThat(ImagesDeTest.contient(sortie.contenu(), "GPSLatitude")).isFalse();
        assertThat(ImagesDeTest.contient(sortie.contenu(), "TelephoneDeTest")).isFalse();
        assertThat(sortie.format()).isEqualTo(FormatImage.JPEG);
    }

    @Test
    void uneGrandePhotoEstRedimensionneeEnGardantSesProportions() {
        PhotoNettoyee sortie = TraitementPhoto.nettoyer(ImagesDeTest.jpeg(3200, 1600));
        assertThat(sortie.largeur()).isEqualTo(1600);
        assertThat(sortie.hauteur()).isEqualTo(800);
    }

    @Test
    void unePetitePhotoNEstPasAgrandie() {
        PhotoNettoyee sortie = TraitementPhoto.nettoyer(ImagesDeTest.jpeg(400, 300));
        assertThat(sortie.largeur()).isEqualTo(400);
    }

    @Test
    void laTransparenceEstConserveeEnPng() {
        assertThat(TraitementPhoto.nettoyer(ImagesDeTest.pngTransparent(100, 100)).format()).isEqualTo(FormatImage.PNG);
    }

    @Test
    void leWebpEstLuEtConvertiEnJpeg() {
        PhotoNettoyee sortie = TraitementPhoto.nettoyer(ImagesDeTest.WEBP_1X1);
        assertThat(sortie.largeur()).isEqualTo(1);
        assertThat(sortie.format()).isIn(FormatImage.JPEG, FormatImage.PNG);
    }

    @Test
    void leTypeEstVerifieParLaSignatureEtNonParLExtension() {
        byte[] texteDeguise = "<?php system($_GET['c']); ?> ceci n'est pas une image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TraitementPhoto.nettoyer(texteDeguise))
                .isInstanceOf(RequeteInvalideException.class)
                .extracting(e -> ((RequeteInvalideException) e).getCode()).isEqualTo("FORMAT_IMAGE_REFUSE");
    }

    @Test
    void uneImageAuxBonsOctetsDeDebutMaisCorrompueEstRefusee() {
        byte[] faux = new byte[64];
        faux[0] = (byte) 0xFF;
        faux[1] = (byte) 0xD8;
        faux[2] = (byte) 0xFF;
        assertThatThrownBy(() -> TraitementPhoto.nettoyer(faux))
                .extracting(e -> ((RequeteInvalideException) e).getCode()).isEqualTo("IMAGE_ILLISIBLE");
    }

    @Test
    void lesDimensionsDemesureesSontRefuseesAvantDecodage() {
        assertThatThrownBy(() -> TraitementPhoto.nettoyer(ImagesDeTest.jpeg(6500, 10)))
                .extracting(e -> ((RequeteInvalideException) e).getCode()).isEqualTo("IMAGE_TROP_GRANDE");
    }

    @Test
    void fichierVideOuTropVolumineux() {
        assertThatThrownBy(() -> TraitementPhoto.nettoyer(new byte[0]))
                .extracting(e -> ((RequeteInvalideException) e).getCode()).isEqualTo("FICHIER_VIDE");
        assertThatThrownBy(() -> TraitementPhoto.nettoyer(new byte[TraitementPhoto.TAILLE_MAX_OCTETS + 1]))
                .extracting(e -> ((RequeteInvalideException) e).getCode()).isEqualTo("FICHIER_TROP_VOLUMINEUX");
    }
}
