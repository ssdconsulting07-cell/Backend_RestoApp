package sn.chezketchup.backend.photos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.imageio.ImageIO;

/** Fabrique d'images pour les tests (dont une photo de telephone avec bloc EXIF GPS). */
public final class ImagesDeTest {

    /** WebP 1x1 sans perte (le JDK ne sait pas en ecrire). */
    public static final byte[] WEBP_1X1 = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

    private ImagesDeTest() {
    }

    public static byte[] jpeg(int largeur, int hauteur) {
        return encoder(image(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    public static byte[] pngTransparent(int largeur, int hauteur) {
        return encoder(image(largeur, hauteur, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /**
     * JPEG avec un segment APP1 "Exif" contenant un faux releve GPS, insere
     * juste apres le marqueur de debut d'image, comme le ferait un telephone.
     */
    public static byte[] jpegAvecExifGps(int largeur, int hauteur) {
        byte[] jpeg = jpeg(largeur, hauteur);
        byte[] charge = "Exif\0\0GPSLatitude=14.6928;GPSLongitude=-17.4467;Model=TelephoneDeTest"
                .getBytes(StandardCharsets.ISO_8859_1);
        int longueur = charge.length + 2;
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        sortie.write(jpeg, 0, 2); // FF D8
        sortie.write(0xFF);
        sortie.write(0xE1); // APP1
        sortie.write((longueur >> 8) & 0xFF);
        sortie.write(longueur & 0xFF);
        sortie.write(charge, 0, charge.length);
        sortie.write(jpeg, 2, jpeg.length - 2);
        return sortie.toByteArray();
    }

    public static boolean contient(byte[] donnees, String texte) {
        return new String(donnees, StandardCharsets.ISO_8859_1).contains(texte);
    }

    private static BufferedImage image(int largeur, int hauteur, int type) {
        BufferedImage img = new BufferedImage(largeur, hauteur, type);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(166, 25, 46, type == BufferedImage.TYPE_INT_ARGB ? 128 : 255));
        g.fillRect(0, 0, largeur, hauteur / 2);
        g.dispose();
        return img;
    }

    private static byte[] encoder(BufferedImage img, String format) {
        try {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            ImageIO.write(img, format, sortie);
            return sortie.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
