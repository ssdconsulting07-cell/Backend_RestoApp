package sn.chezketchup.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import sn.chezketchup.backend.photos.ImagesDeTest;

/** Depot et lecture des photos, de bout en bout (multipart reel). */
class PhotosApiIT extends IntegrationTest {

    private static final String LIMITE = "----limiteSenYummies";

    private Reponse deposer(byte[] contenu, String nomFichier, String typeDeclare, String jeton) {
        ByteArrayOutputStream corps = new ByteArrayOutputStream();
        String debut = "--" + LIMITE + "\r\nContent-Disposition: form-data; name=\"fichier\"; filename=\""
                + nomFichier + "\"\r\nContent-Type: " + typeDeclare + "\r\n\r\n";
        corps.writeBytes(debut.getBytes(StandardCharsets.UTF_8));
        corps.writeBytes(contenu);
        corps.writeBytes(("\r\n--" + LIMITE + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder b = HttpRequest.newBuilder(url("/photos"))
                .header("Content-Type", "multipart/form-data; boundary=" + LIMITE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corps.toByteArray()));
        if (jeton != null) {
            b.header("Authorization", "Bearer " + jeton);
        }
        return executer(b.build());
    }

    private HttpResponse<byte[]> lireOctets(String chemin) throws Exception {
        return http.send(HttpRequest.newBuilder(url(chemin)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void photoDeposeeNettoyeeEtServie() throws Exception {
        Reponse r = deposer(ImagesDeTest.jpegAvecExifGps(2400, 1200), "IMG_2026.jpg", "image/jpeg", jeton("GERANT"));
        assertThat(r.statut()).as(r.corps()).isEqualTo(201);
        String url = r.json().get("url").asString();
        assertThat(url).matches("/api/v1/photos/[a-f0-9-]{36}\\.jpg");
        assertThat(r.json().get("largeur").asInt()).isEqualTo(1600);

        HttpResponse<byte[]> photo = lireOctets(url.substring("/api/v1".length()));
        assertThat(photo.statusCode()).isEqualTo(200);
        assertThat(photo.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(photo.headers().firstValue("Cache-Control").orElse("")).contains("immutable");
        assertThat(ImagesDeTest.contient(photo.body(), "GPSLatitude")).as("EXIF retire").isFalse();
    }

    @Test
    void unFichierDeguiseEnImageEstRefuse() {
        Reponse r = deposer("<script>alert(1)</script>".repeat(10).getBytes(StandardCharsets.UTF_8),
                "photo.jpg", "image/jpeg", jeton("GERANT"));
        assertThat(r.statut()).isEqualTo(400);
        assertThat(r.json().get("code").asString()).isEqualTo("FORMAT_IMAGE_REFUSE");
        assertThat(r.json().get("field").asString()).isEqualTo("fichier");
    }

    @Test
    void auDelaDeDeuxMoLeFichierEstRefuse() {
        byte[] lourd = new byte[2 * 1024 * 1024 + 50_000];
        lourd[0] = (byte) 0xFF;
        lourd[1] = (byte) 0xD8;
        lourd[2] = (byte) 0xFF;
        assertThat(deposer(lourd, "lourd.jpg", "image/jpeg", jeton("GERANT")).statut()).isEqualTo(413);
    }

    @Test
    void seulLeGerantDepose() {
        byte[] img = ImagesDeTest.jpeg(50, 50);
        assertThat(deposer(img, "a.jpg", "image/jpeg", null).statut()).isEqualTo(401);
        assertThat(deposer(img, "a.jpg", "image/jpeg", jeton("CUISINE")).statut()).isEqualTo(403);
    }

    @Test
    void unNomDeFichierArbitraireNeSortJamaisDuDossier() throws Exception {
        assertThat(lireOctets("/photos/..%2F..%2Fapplication.properties").statusCode()).isIn(400, 404);
        assertThat(lireOctets("/photos/inexistante.jpg").statusCode()).isEqualTo(404);
    }
}
