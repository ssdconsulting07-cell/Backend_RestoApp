package sn.chezketchup.backend.photos.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.chezketchup.backend.photos.application.PhotoService;
import sn.chezketchup.backend.photos.application.PhotoService.PhotoDeposee;
import sn.chezketchup.backend.photos.application.PhotoStorage.PhotoStockee;

@RestController
@Tag(name = "Photos")
public class PhotoController {

    private final PhotoService photos;

    public PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Deposer une photo (JPEG, PNG ou WebP, 2 Mo max) : verifiee, redimensionnee, sans EXIF")
    public PhotoDeposee deposer(@RequestParam("fichier") MultipartFile fichier) throws IOException {
        return photos.deposer(fichier.getBytes());
    }

    /** Nom unique par photo : cache navigateur d'un an, sans revalidation. */
    @GetMapping("/photos/{nom}")
    public ResponseEntity<byte[]> lire(@PathVariable String nom) {
        PhotoStockee photo = photos.lire(nom);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.typeMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.contenu());
    }
}
