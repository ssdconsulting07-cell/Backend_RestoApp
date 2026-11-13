package sn.chezketchup.backend.photos.domaine;

import java.util.Optional;

/**
 * Formats acceptes en entree, reconnus par leur signature binaire (les
 * premiers octets du fichier), jamais par l'extension ou le Content-Type
 * envoyes par le navigateur, qui se falsifient.
 */
public enum FormatImage {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String typeMime;

    FormatImage(String extension, String typeMime) {
        this.extension = extension;
        this.typeMime = typeMime;
    }

    public String extension() {
        return extension;
    }

    public String typeMime() {
        return typeMime;
    }

    public static Optional<FormatImage> detecter(byte[] o) {
        if (o == null || o.length < 12) {
            return Optional.empty();
        }
        if ((o[0] & 0xFF) == 0xFF && (o[1] & 0xFF) == 0xD8 && (o[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if ((o[0] & 0xFF) == 0x89 && o[1] == 'P' && o[2] == 'N' && o[3] == 'G'
                && o[4] == 0x0D && o[5] == 0x0A && o[6] == 0x1A && o[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (o[0] == 'R' && o[1] == 'I' && o[2] == 'F' && o[3] == 'F'
                && o[8] == 'W' && o[9] == 'E' && o[10] == 'B' && o[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
