package sn.chezketchup.backend.annonces.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.annonces.infrastructure.AnnonceRepository;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/** Annonces publiees, en cache (bean separe pour que @Cacheable passe par le proxy). */
@Component
@Transactional(readOnly = true)
public class AnnoncesPubliques {

    public static final String CACHE = "annonces";

    private final AnnonceRepository annonces;

    public AnnoncesPubliques(AnnonceRepository annonces) {
        this.annonces = annonces;
    }

    @Cacheable(CACHE)
    public List<AnnonceVue> lister() {
        return annonces.rechercher(StatutPublication.ACTIF).stream().map(AnnonceVue::de).toList();
    }
}
