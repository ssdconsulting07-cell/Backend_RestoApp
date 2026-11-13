package sn.chezketchup.backend.photos.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.photos.domaine.FormatImage;

/**
 * Nettoyage d'une photo deposee :
 * 1. type reel verifie par signature (JPEG, PNG, WebP) ;
 * 2. dimensions lues AVANT decodage et plafonnees (une "bombe de
 *    decompression" de quelques Ko peut sinon reclamer des Go de memoire) ;
 * 3. redimensionnement (cote la plus longue <= 1600 px) ;
 * 4. reencodage complet en JPEG (ou PNG si transparence) : seuls les pixels
 *    sont conserves, donc toutes les metadonnees (EXIF : position GPS du
 *    telephone, modele, date...) disparaissent, ainsi que tout contenu
 *    cache dans le fichier d'origine.
 *
 * Limite connue : l'orientation EXIF n'est pas appliquee. Le Manager
 * recadre deja les photos dans le navigateur (canvas), qui l'applique.
 */
public final class TraitementPhoto {

    public static final int TAILLE_MAX_OCTETS = 2 * 1024 * 1024;
    static final int COTE_MAX_ENTREE = 6000;
    static final long PIXELS_MAX_ENTREE = 36_000_000L;
    static final int COTE_MAX_SORTIE = 1600;
    private static final float QUALITE_JPEG = 0.85f;

    private TraitementPhoto() {
    }

    public record PhotoNettoyee(byte[] contenu, FormatImage format, int largeur, int hauteur) {
    }

    public static PhotoNettoyee nettoyer(byte[] entree) {
        if (entree == null || entree.length == 0) {
            throw refus("FICHIER_VIDE", "Fichier vide.");
        }
        if (entree.length > TAILLE_MAX_OCTETS) {
            throw refus("FICHIER_TROP_VOLUMINEUX", "Fichier trop volumineux (2 Mo maximum).");
        }
        FormatImage format = FormatImage.detecter(entree)
                .orElseThrow(() -> refus("FORMAT_IMAGE_REFUSE", "Seules les images JPEG, PNG et WebP sont acceptées."));
        BufferedImage image = lireAvecGardeFou(entree, format);
        BufferedImage reduite = reduire(image);
        boolean transparence = reduite.getColorModel().hasAlpha();
        try {
            return transparence
                    ? new PhotoNettoyee(ecrirePng(reduite), FormatImage.PNG, reduite.getWidth(), reduite.getHeight())
                    : new PhotoNettoyee(ecrireJpeg(versRgb(reduite)), FormatImage.JPEG, reduite.getWidth(), reduite.getHeight());
        } catch (IOException e) {
            throw new IllegalStateException("Encodage de la photo impossible", e);
        }
    }

    private static BufferedImage lireAvecGardeFou(byte[] entree, FormatImage format) {
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(entree))) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) {
                throw refus("IMAGE_ILLISIBLE", "Image illisible ou endommagée.");
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur <= 0 || hauteur <= 0 || largeur > COTE_MAX_ENTREE || hauteur > COTE_MAX_ENTREE
                        || (long) largeur * hauteur > PIXELS_MAX_ENTREE) {
                    throw refus("IMAGE_TROP_GRANDE", "Image trop grande (6000 × 6000 pixels maximum).");
                }
                BufferedImage image = lecteur.read(0);
                if (image == null) {
                    throw refus("IMAGE_ILLISIBLE", "Image illisible ou endommagée.");
                }
                return image;
            } finally {
                lecteur.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof RequeteInvalideException r) {
                throw r;
            }
            throw refus("IMAGE_ILLISIBLE", "Image illisible ou endommagée (" + format.name() + ").");
        }
    }

    static BufferedImage reduire(BufferedImage image) {
        int l = image.getWidth();
        int h = image.getHeight();
        int cote = Math.max(l, h);
        if (cote <= COTE_MAX_SORTIE) {
            return image;
        }
        double ratio = (double) COTE_MAX_SORTIE / cote;
        int nl = Math.max(1, (int) Math.round(l * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage cible = new BufferedImage(nl, nh, type);
        Graphics2D g = cible.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, 0, 0, nl, nh, null);
        } finally {
            g.dispose();
        }
        return cible;
    }

    private static BufferedImage versRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static byte[] ecrireJpeg(BufferedImage image) throws IOException {
        ImageWriter ecrivain = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ImageOutputStream flux = ImageIO.createImageOutputStream(sortie)) {
            ecrivain.setOutput(flux);
            ImageWriteParam param = ecrivain.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITE_JPEG);
            // Aucune metadonnee transmise (null) : fichier "propre".
            ecrivain.write(null, new IIOImage(image, null, null), param);
        } finally {
            ecrivain.dispose();
        }
        return sortie.toByteArray();
    }

    private static byte[] ecrirePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
    }

    private static RequeteInvalideException refus(String code, String message) {
        return new RequeteInvalideException(code, message, "fichier");
    }
}
