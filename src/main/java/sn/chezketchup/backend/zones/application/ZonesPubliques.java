package sn.chezketchup.backend.zones.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.commun.transaction.LectureSeule;
import sn.chezketchup.backend.zones.application.ZonesVues.RegionVue;
import sn.chezketchup.backend.zones.application.ZonesVues.SecteurVue;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;

/**
 * Lectures publiques des zones, en cache (videes par ZonesService a chaque
 * ecriture). Transaction ouverte au remplissage seulement (LectureSeule) :
 * une lecture servie par le cache ne touche pas la base.
 */
@Component
public class ZonesPubliques {

    public static final String REGIONS = "regions";
    public static final String SECTEURS = "secteurs";

    private final RegionRepository regions;
    private final SecteurRepository secteurs;
    private final LectureSeule lecture;

    public ZonesPubliques(RegionRepository regions, SecteurRepository secteurs, LectureSeule lecture) {
        this.regions = regions;
        this.secteurs = secteurs;
        this.lecture = lecture;
    }

    @Cacheable(REGIONS)
    public List<RegionVue> regions(Boolean active) {
        return lecture.executer(() -> (active == null ? regions.findAllByOrderByNomAsc()
                : regions.findByActiveOrderByNomAsc(active)).stream().map(RegionVue::de).toList());
    }

    /**
     * Zones proposees aux clients : region ouverte a la livraison, zones non
     * masquees. Region inconnue : 404 (une exception n'est pas mise en cache).
     */
    @Cacheable(SECTEURS)
    public List<SecteurVue> secteurs(String regionCode) {
        return lecture.executer(() -> {
            boolean ouverte = regions.findById(regionCode)
                    .orElseThrow(() -> new RessourceIntrouvableException("Région introuvable."))
                    .isActive();
            if (!ouverte) {
                return List.<SecteurVue>of();
            }
            return secteurs.findByRegionCodeAndEstMasqueFalseOrderByDepartementAscNomAsc(regionCode)
                    .stream().map(SecteurVue::de).toList();
        });
    }
}
