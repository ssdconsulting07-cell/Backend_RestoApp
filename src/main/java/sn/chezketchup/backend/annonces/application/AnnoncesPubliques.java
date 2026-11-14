package sn.chezketchup.backend.annonces.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import sn.chezketchup.backend.annonces.infrastructure.AnnonceRepository;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.transaction.LectureSeule;

/**
 * Annonces publiees, en cache (bean separe pour que @Cacheable passe par le
 * proxy). Transaction ouverte au remplissage seulement (LectureSeule) : une
 * lecture servie par le cache ne touche pas la base.
 */
@Component
public class AnnoncesPubliques {

    public static final String CACHE = "annonces";

    private final AnnonceRepository annonces;
    private final LectureSeule lecture;

    public AnnoncesPubliques(AnnonceRepository annonces, LectureSeule lecture) {
        this.annonces = annonces;
        this.lecture = lecture;
    }

    @Cacheable(CACHE)
    public List<AnnonceVue> lister() {
        return lecture.executer(() -> annonces.rechercher(StatutPublication.ACTIF).stream()
                .map(AnnonceVue::de).toList());
    }
}
