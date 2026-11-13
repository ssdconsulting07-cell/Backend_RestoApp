package sn.chezketchup.backend.photos.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.photos.application.PhotoStorage;
import sn.chezketchup.backend.photos.domaine.FormatImage;

/**
 * Stockage des photos sur disque (dossier app.photos.dossier, volume Docker
 * en conteneur). Noms aleatoires generes ici : un nom fourni par le client
 * n'atteint jamais le systeme de fichiers (pas de traversee de chemin).
 */
@Component
public class DisquePhotoStorage implements PhotoStorage {

    private static final Pattern NOM_VALIDE = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(jpg|png)$");

    private final Path dossier;

    public DisquePhotoStorage(@Value("${app.photos.dossier}") String dossier) {
        this.dossier = Path.of(dossier).toAbsolutePath().normalize();
    }

    @Override
    public String enregistrer(byte[] contenu, FormatImage format) {
        String nom = UUID.randomUUID() + "." + format.extension();
        try {
            Files.createDirectories(dossier);
            // Ecriture atomique : jamais de fichier a moitie ecrit servi a un client.
            Path temporaire = Files.createTempFile(dossier, "televersement-", ".tmp");
            Files.write(temporaire, contenu);
            Files.move(temporaire, dossier.resolve(nom), StandardCopyOption.ATOMIC_MOVE);
            return nom;
        } catch (IOException e) {
            throw new UncheckedIOException("Enregistrement de la photo impossible", e);
        }
    }

    @Override
    public Optional<PhotoStockee> lire(String nom) {
        if (nom == null || !NOM_VALIDE.matcher(nom).matches()) {
            return Optional.empty();
        }
        Path fichier = dossier.resolve(nom).normalize();
        if (!fichier.startsWith(dossier) || !Files.isRegularFile(fichier)) {
            return Optional.empty();
        }
        try {
            String type = nom.endsWith(".png") ? FormatImage.PNG.typeMime() : FormatImage.JPEG.typeMime();
            return Optional.of(new PhotoStockee(Files.readAllBytes(fichier), type));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la photo impossible", e);
        }
    }
}
