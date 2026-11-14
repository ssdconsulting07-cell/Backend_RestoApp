package sn.chezketchup.backend.catalogue.application;

import java.util.List;

import org.springframework.stereotype.Service;

import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.publication.Visibilite;
import sn.chezketchup.backend.commun.transaction.LectureSeule;

/**
 * Lecture du catalogue. Visiteur et staff hors Gerant : contenu publie
 * (cache) ; Gerant : etat reel, filtre par statut a la demande.
 *
 * Pas de @Transactional sur la classe : la vue publique est servie par le
 * cache sans toucher la base ; les autres lectures ouvrent leur transaction
 * (LectureSeule).
 */
@Service
public class CatalogueService {

    private final CatalogueCachePublic cache;
    private final CategorieRepository categories;
    private final ProduitRepository produits;
    private final PlatDuJourRepository plats;
    private final AccompagnementCondimentRepository accompagnements;
    private final LectureSeule lecture;

    public CatalogueService(CatalogueCachePublic cache, CategorieRepository categories, ProduitRepository produits,
                            PlatDuJourRepository plats, AccompagnementCondimentRepository accompagnements,
                            LectureSeule lecture) {
        this.cache = cache;
        this.categories = categories;
        this.produits = produits;
        this.plats = plats;
        this.accompagnements = accompagnements;
        this.lecture = lecture;
    }

    public List<CategorieVue> categories(StatutPublication demande) {
        StatutPublication statut = Visibilite.statutAFiltrer(demande);
        if (Visibilite.estVuePublique()) {
            return cache.categories();
        }
        return lecture.executer(() -> (statut == null ? categories.findAllByOrderByNomAsc()
                : categories.findByStatutOrderByNomAsc(statut)).stream().map(CategorieVue::de).toList());
    }

    public List<ProduitVue> produits(StatutPublication demande, Long categorieId) {
        StatutPublication statut = Visibilite.statutAFiltrer(demande);
        if (Visibilite.estVuePublique()) {
            return cache.produits(categorieId);
        }
        return lecture.executer(() -> produits.rechercher(statut, categorieId).stream().map(ProduitVue::de).toList());
    }

    /** Detail : hors Gerant, un produit non publie (ou de categorie non publiee) est introuvable. */
    public ProduitVue produit(long id) {
        return lecture.executer(() -> detail(id));
    }

    private ProduitVue detail(long id) {
        Produit p = produits.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable."));
        if (Visibilite.estVuePublique() && !estPublic(p)) {
            throw new RessourceIntrouvableException("Produit introuvable.");
        }
        return ProduitVue.de(p);
    }

    public List<PlatDuJourVue> plats(StatutPublication demande, JourSemaine jour) {
        StatutPublication statut = Visibilite.statutAFiltrer(demande);
        if (Visibilite.estVuePublique()) {
            return cache.plats(jour);
        }
        return lecture.executer(() -> plats.rechercher(statut, jour).stream().map(PlatDuJourVue::de).toList());
    }

    public List<AccompagnementCondimentVue> accompagnements(StatutPublication demande, TypeAccompagnement type) {
        StatutPublication statut = Visibilite.statutAFiltrer(demande);
        if (Visibilite.estVuePublique()) {
            return cache.accompagnements(type);
        }
        return lecture.executer(() -> accompagnements.rechercher(statut, type).stream()
                .map(AccompagnementCondimentVue::de).toList());
    }

    private boolean estPublic(Produit p) {
        return p.getStatut() == StatutPublication.ACTIF
                && categories.findById(p.getCategorieId())
                        .map(c -> c.getStatut() == StatutPublication.ACTIF).orElse(false);
    }
}
