package sn.chezketchup.backend.catalogue.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;
import static sn.chezketchup.backend.commun.audit.Libelles.guillemets;
import static sn.chezketchup.backend.commun.audit.Libelles.verbeStatut;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.catalogue.domaine.AccompagnementCondiment;
import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.PlatDuJour;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.web.Identifiants;

/**
 * Ecritures du Gerant sur le catalogue. Chaque methode : une transaction
 * (fiche + audit ensemble, ou rien), cache public vide ensuite.
 */
@Service
@Transactional
public class GestionCatalogueService {

    private final CategorieRepository categories;
    private final ProduitRepository produits;
    private final PlatDuJourRepository plats;
    private final AccompagnementCondimentRepository accompagnements;
    private final JournalActions journal;

    public GestionCatalogueService(CategorieRepository categories, ProduitRepository produits,
                                   PlatDuJourRepository plats, AccompagnementCondimentRepository accompagnements,
                                   JournalActions journal) {
        this.categories = categories;
        this.produits = produits;
        this.plats = plats;
        this.accompagnements = accompagnements;
        this.journal = journal;
    }

    // --- Categories ----------------------------------------------------------

    @CacheEvict(cacheNames = CatalogueCachePublic.CATEGORIES, allEntries = true)
    public CategorieVue creerCategorie(Saisies.Categorie s) {
        if (categories.existsByNomIgnoreCase(s.nom().trim())) {
            throw nomDejaUtilise();
        }
        Categorie c = categories.save(new Categorie(s.nom(), Boolean.TRUE.equals(s.accompagnementPropose())));
        journal.enregistrer(Domaine.CATEGORIE, "CREATION", id(c.getId()), "a créé la catégorie " + guillemets(c.getNom()));
        return CategorieVue.de(c);
    }

    @CacheEvict(cacheNames = {CatalogueCachePublic.CATEGORIES, CatalogueCachePublic.PRODUITS}, allEntries = true)
    public CategorieVue modifierCategorie(long id, Saisies.Categorie s) {
        Categorie c = categorie(id);
        c.verifierVersion(s.version());
        if (categories.existsByNomIgnoreCaseAndIdNot(s.nom().trim(), id)) {
            throw nomDejaUtilise();
        }
        c.modifier(s.nom(), s.accompagnementPropose() != null ? s.accompagnementPropose() : c.isAccompagnementPropose());
        journal.enregistrer(Domaine.CATEGORIE, "MODIFICATION", id(id), "a modifié la catégorie " + guillemets(c.getNom()));
        return CategorieVue.de(c);
    }

    /**
     * Archiver une categorie qui contient encore des produits non archives
     * demande de les reaffecter a une autre categorie (meme regle que la
     * fenetre "Reaffecter" du Manager) ; sinon 409 CATEGORIE_NON_VIDE.
     */
    @CacheEvict(cacheNames = {CatalogueCachePublic.CATEGORIES, CatalogueCachePublic.PRODUITS}, allEntries = true)
    public CategorieVue changerStatutCategorie(long id, Saisies.ChangementStatut s) {
        Categorie c = categorie(id);
        c.verifierVersion(s.version());
        StatutPublication avant = c.getStatut();
        if (s.statut() == StatutPublication.ARCHIVE && avant != StatutPublication.ARCHIVE) {
            reaffecterProduits(c, s.reassignerVers());
        }
        if (c.changerStatut(s.statut())) {
            journal.enregistrer(Domaine.CATEGORIE, "STATUT_" + s.statut(), id(id),
                    verbeStatut(avant, s.statut()) + " la catégorie " + guillemets(c.getNom()));
        }
        return CategorieVue.de(c);
    }

    private void reaffecterProduits(Categorie origine, String reassignerVers) {
        List<Produit> restants = produits.findByCategorieIdAndStatutNot(origine.getId(), StatutPublication.ARCHIVE);
        if (restants.isEmpty()) {
            return;
        }
        Long cibleId = Identifiants.champ(reassignerVers, "reassignerVers");
        if (cibleId == null) {
            throw new ConflitException("CATEGORIE_NON_VIDE", restants.size()
                    + " produit(s) dans cette catégorie : choisissez la catégorie qui les reçoit (reassignerVers).");
        }
        Categorie cible = categories.findById(cibleId)
                .orElseThrow(() -> new RequeteInvalideException("CATEGORIE_INCONNUE", "Catégorie de destination introuvable.", "reassignerVers"));
        if (cible.getId().equals(origine.getId()) || cible.getStatut() == StatutPublication.ARCHIVE) {
            throw new RequeteInvalideException("CATEGORIE_INVALIDE",
                    "La catégorie de destination doit être une autre catégorie non archivée.", "reassignerVers");
        }
        restants.forEach(p -> p.changerCategorie(cible.getId()));
        journal.enregistrer(Domaine.CATEGORIE, "REAFFECTATION", id(origine.getId()), "a déplacé " + restants.size()
                + " produit(s) de " + guillemets(origine.getNom()) + " vers " + guillemets(cible.getNom()));
    }

    // --- Produits ---------------------------------------------------------------

    @CacheEvict(cacheNames = CatalogueCachePublic.PRODUITS, allEntries = true)
    public ProduitVue creerProduit(Saisies.Produit s) {
        Long categorieId = categorieUtilisable(s.categorieId());
        Produit p = produits.save(new Produit(s.nom(), s.description(), s.prix(), categorieId, s.photoUrl(),
                s.disponibilite(), Boolean.TRUE.equals(s.tendance())));
        journal.enregistrer(Domaine.PRODUIT, "CREATION", id(p.getId()), "a créé le produit " + guillemets(p.getNom()));
        return ProduitVue.de(p);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.PRODUITS, allEntries = true)
    public ProduitVue modifierProduit(long id, Saisies.Produit s) {
        Produit p = produit(id);
        p.verifierVersion(s.version());
        Long categorieId = s.categorieId().equals(String.valueOf(p.getCategorieId()))
                ? p.getCategorieId() : categorieUtilisable(s.categorieId());
        long ancienPrix = p.getPrix();
        Disponibilite ancienneDispo = p.getDisponibilite();
        p.modifier(s.nom(), s.description(), s.prix(), categorieId, s.photoUrl(),
                s.disponibilite() != null ? s.disponibilite() : ancienneDispo,
                s.tendance() != null ? s.tendance() : p.isTendance());
        journal.enregistrer(Domaine.PRODUIT, "MODIFICATION", id(id), messageModification(p, ancienPrix, ancienneDispo));
        return ProduitVue.de(p);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.PRODUITS, allEntries = true)
    public ProduitVue changerStatutProduit(long id, Saisies.ChangementStatut s) {
        Produit p = produit(id);
        p.verifierVersion(s.version());
        StatutPublication avant = p.getStatut();
        if (s.statut() == StatutPublication.ACTIF) {
            categories.findById(p.getCategorieId())
                    .filter(c -> c.getStatut() == StatutPublication.ARCHIVE)
                    .ifPresent(c -> {
                        throw new ConflitException("CATEGORIE_ARCHIVEE",
                                "Sa catégorie est archivée : changez de catégorie avant de publier.");
                    });
        }
        if (p.changerStatut(s.statut())) {
            journal.enregistrer(Domaine.PRODUIT, "STATUT_" + s.statut(), id(id),
                    verbeStatut(avant, s.statut()) + " le produit " + guillemets(p.getNom()));
        }
        return ProduitVue.de(p);
    }

    private static String messageModification(Produit p, long ancienPrix, Disponibilite ancienneDispo) {
        if (ancienPrix != p.getPrix()) {
            return "a modifié le prix de " + guillemets(p.getNom()) + " : " + fcfa(ancienPrix) + " → " + fcfa(p.getPrix());
        }
        if (ancienneDispo != p.getDisponibilite()) {
            return p.getDisponibilite() == Disponibilite.RUPTURE
                    ? "a mis le produit " + guillemets(p.getNom()) + " en rupture"
                    : "a remis le produit " + guillemets(p.getNom()) + " en stock";
        }
        return "a modifié le produit " + guillemets(p.getNom());
    }

    // --- Plats du jour --------------------------------------------------------------

    @CacheEvict(cacheNames = CatalogueCachePublic.PLATS_DU_JOUR, allEntries = true)
    public PlatDuJourVue creerPlat(Saisies.PlatDuJour s) {
        PlatDuJour p = plats.save(new PlatDuJour(s.nom(), s.description(), s.prix(), s.photoUrl(), s.jour()));
        journal.enregistrer(Domaine.PRODUIT, "CREATION", id(p.getId()), "a créé le plat du jour " + guillemets(p.getNom()));
        return PlatDuJourVue.de(p);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.PLATS_DU_JOUR, allEntries = true)
    public PlatDuJourVue modifierPlat(long id, Saisies.PlatDuJour s) {
        PlatDuJour p = plat(id);
        p.verifierVersion(s.version());
        p.modifier(s.nom(), s.description(), s.prix(), s.photoUrl(), s.jour());
        journal.enregistrer(Domaine.PRODUIT, "MODIFICATION", id(id), "a modifié le plat du jour " + guillemets(p.getNom()));
        return PlatDuJourVue.de(p);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.PLATS_DU_JOUR, allEntries = true)
    public PlatDuJourVue changerStatutPlat(long id, Saisies.ChangementStatut s) {
        PlatDuJour p = plat(id);
        p.verifierVersion(s.version());
        StatutPublication avant = p.getStatut();
        if (p.changerStatut(s.statut())) {
            journal.enregistrer(Domaine.PRODUIT, "STATUT_" + s.statut(), id(id),
                    verbeStatut(avant, s.statut()) + " le plat du jour " + guillemets(p.getNom()));
        }
        return PlatDuJourVue.de(p);
    }

    // --- Accompagnements et condiments -------------------------------------------------

    @CacheEvict(cacheNames = CatalogueCachePublic.ACCOMPAGNEMENTS, allEntries = true)
    public AccompagnementCondimentVue creerAccompagnement(Saisies.AccompagnementCondiment s) {
        AccompagnementCondiment a = accompagnements.save(new AccompagnementCondiment(s.nom(), s.prix(), s.type()));
        journal.enregistrer(Domaine.PRODUIT, "CREATION", id(a.getId()), "a créé " + guillemets(a.getNom()));
        return AccompagnementCondimentVue.de(a);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.ACCOMPAGNEMENTS, allEntries = true)
    public AccompagnementCondimentVue modifierAccompagnement(long id, Saisies.AccompagnementCondiment s) {
        AccompagnementCondiment a = accompagnement(id);
        a.verifierVersion(s.version());
        a.modifier(s.nom(), s.prix(), s.type());
        journal.enregistrer(Domaine.PRODUIT, "MODIFICATION", id(id), "a modifié " + guillemets(a.getNom()));
        return AccompagnementCondimentVue.de(a);
    }

    @CacheEvict(cacheNames = CatalogueCachePublic.ACCOMPAGNEMENTS, allEntries = true)
    public AccompagnementCondimentVue changerStatutAccompagnement(long id, Saisies.ChangementStatut s) {
        AccompagnementCondiment a = accompagnement(id);
        a.verifierVersion(s.version());
        StatutPublication avant = a.getStatut();
        if (a.changerStatut(s.statut())) {
            journal.enregistrer(Domaine.PRODUIT, "STATUT_" + s.statut(), id(id),
                    verbeStatut(avant, s.statut()) + " " + guillemets(a.getNom()));
        }
        return AccompagnementCondimentVue.de(a);
    }

    // --- Utilitaires ---------------------------------------------------------------------

    private Long categorieUtilisable(String categorieId) {
        Long cid = Identifiants.champ(categorieId, "categorieId");
        Categorie c = categories.findById(cid)
                .orElseThrow(() -> new RequeteInvalideException("CATEGORIE_INCONNUE", "Catégorie introuvable.", "categorieId"));
        if (c.getStatut() == StatutPublication.ARCHIVE) {
            throw new RequeteInvalideException("CATEGORIE_ARCHIVEE", "Cette catégorie est archivée.", "categorieId");
        }
        return cid;
    }

    private Categorie categorie(long id) {
        return categories.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable."));
    }

    private Produit produit(long id) {
        return produits.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable."));
    }

    private PlatDuJour plat(long id) {
        return plats.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Plat du jour introuvable."));
    }

    private AccompagnementCondiment accompagnement(long id) {
        return accompagnements.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Fiche introuvable."));
    }

    private static ConflitException nomDejaUtilise() {
        return new ConflitException("NOM_DEJA_UTILISE", "Une catégorie porte déjà ce nom.");
    }

    private static String id(Long id) {
        return String.valueOf(id);
    }
}
