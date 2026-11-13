package sn.chezketchup.backend.photos.application;

import org.springframework.stereotype.Service;

import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.photos.application.PhotoStorage.PhotoStockee;

@Service
public class PhotoService {

    /** Chemin public des photos (meme adresse que l'API, derriere le relais nginx des fronts). */
    public static final String CHEMIN_PUBLIC = "/api/v1/photos/";

    private final PhotoStorage stockage;
    private final JournalActions journal;

    public PhotoService(PhotoStorage stockage, JournalActions journal) {
        this.stockage = stockage;
        this.journal = journal;
    }

    public record PhotoDeposee(String url, int largeur, int hauteur, int octets) {
    }

    public PhotoDeposee deposer(byte[] contenu) {
        TraitementPhoto.PhotoNettoyee photo = TraitementPhoto.nettoyer(contenu);
        String nom = stockage.enregistrer(photo.contenu(), photo.format());
        journal.enregistrer(Domaine.PRODUIT, "PHOTO", nom, "a déposé une photo");
        return new PhotoDeposee(CHEMIN_PUBLIC + nom, photo.largeur(), photo.hauteur(), photo.contenu().length);
    }

    public PhotoStockee lire(String nom) {
        return stockage.lire(nom).orElseThrow(() -> new RessourceIntrouvableException("Photo introuvable."));
    }
}
