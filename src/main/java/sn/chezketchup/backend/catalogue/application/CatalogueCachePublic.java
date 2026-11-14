package sn.chezketchup.backend.catalogue.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.transaction.LectureSeule;

/**
 * Lectures publiques (contenu ACTIF) mises en cache memoire : le menu est lu
 * par chaque client a chaque ouverture de l'app, mais ne change que quand le
 * Gerant publie. Vide par GestionCatalogueService a chaque ecriture.
 *
 * Bean separe : un @Cacheable appele depuis la meme classe ne passerait pas
 * par le proxy Spring et ne serait jamais mis en cache.
 *
 * Pas de @Transactional sur la classe : une lecture servie par le cache ne
 * touche pas la base (ni connexion, ni transaction) ; la transaction n'est
 * ouverte qu'au remplissage (LectureSeule).
 *
 * Plusieurs instances du backend : remplacer par un cache partage (Redis) ou
 * accepter un decalage borne par une duree de vie (voir document "Reste a
 * faire", point 10).
 */
@Component
public class CatalogueCachePublic {

    public static final String CATEGORIES = "categories";
    public static final String PRODUITS = "produits";
    public static final String PLATS_DU_JOUR = "platsDuJour";
    public static final String ACCOMPAGNEMENTS = "accompagnements";

    private final CategorieRepository categories;
    private final ProduitRepository produits;
    private final PlatDuJourRepository plats;
    private final AccompagnementCondimentRepository accompagnements;
    private final LectureSeule lecture;

    public CatalogueCachePublic(CategorieRepository categories, ProduitRepository produits,
                                PlatDuJourRepository plats, AccompagnementCondimentRepository accompagnements,
                                LectureSeule lecture) {
        this.categories = categories;
        this.produits = produits;
        this.plats = plats;
        this.accompagnements = accompagnements;
        this.lecture = lecture;
    }

    @Cacheable(CATEGORIES)
    public List<CategorieVue> categories() {
        return lecture.executer(() -> categories.findByStatutOrderByNomAsc(StatutPublication.ACTIF).stream()
                .map(CategorieVue::de).toList());
    }

    @Cacheable(PRODUITS)
    public List<ProduitVue> produits(Long categorieId) {
        return lecture.executer(() -> produits.publics(categorieId).stream().map(ProduitVue::de).toList());
    }

    @Cacheable(PLATS_DU_JOUR)
    public List<PlatDuJourVue> plats(JourSemaine jour) {
        return lecture.executer(() -> plats.rechercher(StatutPublication.ACTIF, jour).stream()
                .map(PlatDuJourVue::de).toList());
    }

    @Cacheable(ACCOMPAGNEMENTS)
    public List<AccompagnementCondimentVue> accompagnements(TypeAccompagnement type) {
        return lecture.executer(() -> accompagnements.rechercher(StatutPublication.ACTIF, type).stream()
                .map(AccompagnementCondimentVue::de).toList());
    }
}
