package sn.chezketchup.backend.photos.application;

import java.util.Optional;

import sn.chezketchup.backend.photos.domaine.FormatImage;

/**
 * Port de stockage des photos. Implementation actuelle : disque local
 * ({@code DisquePhotoStorage}, volume Docker). Pour un stockage objet
 * (S3, Cloudflare R2, MinIO...) : ecrire une autre implementation et
 * l'activer par configuration, sans toucher au reste (voir "Reste a faire").
 */
public interface PhotoStorage {

    /** Enregistre une photo deja nettoyee ; renvoie son nom (genere par le serveur, jamais par le client). */
    String enregistrer(byte[] contenu, FormatImage format);

    Optional<PhotoStockee> lire(String nom);

    record PhotoStockee(byte[] contenu, String typeMime) {
    }
}
