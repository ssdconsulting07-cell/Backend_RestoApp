package sn.chezketchup.backend.zones.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.zones.application.ZonesVues.RegionVue;
import sn.chezketchup.backend.zones.application.ZonesVues.SecteurVue;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;

/** Lectures publiques des zones, en cache (videes par ZonesService a chaque ecriture). */
@Component
@Transactional(readOnly = true)
public class ZonesPubliques {

    public static final String REGIONS = "regions";
    public static final String SECTEURS = "secteurs";

    private final RegionRepository regions;
    private final SecteurRepository secteurs;

    public ZonesPubliques(RegionRepository regions, SecteurRepository secteurs) {
        this.regions = regions;
        this.secteurs = secteurs;
    }

    @Cacheable(REGIONS)
    public List<RegionVue> regions(Boolean active) {
        return (active == null ? regions.findAllByOrderByNomAsc() : regions.findByActiveOrderByNomAsc(active))
                .stream().map(RegionVue::de).toList();
    }

    /** Zones proposees aux clients : region ouverte a la livraison, zones non masquees. */
    @Cacheable(SECTEURS)
    public List<SecteurVue> secteurs(String regionCode) {
        boolean ouverte = regions.findById(regionCode).map(r -> r.isActive()).orElse(false);
        if (!ouverte) {
            return List.of();
        }
        return secteurs.findByRegionCodeAndEstMasqueFalseOrderByDepartementAscNomAsc(regionCode)
                .stream().map(SecteurVue::de).toList();
    }
}
