package sn.chezketchup.backend.annonces.application;

import static sn.chezketchup.backend.commun.audit.Libelles.guillemets;
import static sn.chezketchup.backend.commun.audit.Libelles.verbeStatut;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.annonces.domaine.Annonce;
import sn.chezketchup.backend.annonces.infrastructure.AnnonceRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.commun.publication.StatutPublication;
import sn.chezketchup.backend.commun.publication.Visibilite;
import sn.chezketchup.backend.commun.transaction.LectureSeule;

/** Annonces du Gerant (popup de l'App Client). */
@Service
@Transactional
public class AnnonceService {

    private final AnnonceRepository annonces;
    private final AnnoncesPubliques publiques;
    private final JournalActions journal;
    private final LectureSeule lecture;

    public AnnonceService(AnnonceRepository annonces, AnnoncesPubliques publiques, JournalActions journal,
                          LectureSeule lecture) {
        this.annonces = annonces;
        this.publiques = publiques;
        this.journal = journal;
        this.lecture = lecture;
    }

    /** Vue publique servie par le cache sans transaction ; Gerant : transaction de lecture. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AnnonceVue> lister(StatutPublication demande) {
        StatutPublication statut = Visibilite.statutAFiltrer(demande);
        if (Visibilite.estVuePublique()) {
            return publiques.lister();
        }
        return lecture.executer(() -> annonces.rechercher(statut).stream().map(AnnonceVue::de).toList());
    }

    @CacheEvict(cacheNames = AnnoncesPubliques.CACHE, allEntries = true)
    public AnnonceVue creer(AnnonceSaisie s) {
        Annonce a = new Annonce(s.titre());
        a.remplacerSlides(slides(s));
        a = annonces.save(a);
        journal.enregistrer(Domaine.ANNONCE, "CREATION", String.valueOf(a.getId()),
                "a créé l'annonce " + guillemets(a.getTitre()));
        return AnnonceVue.de(a);
    }

    @CacheEvict(cacheNames = AnnoncesPubliques.CACHE, allEntries = true)
    public AnnonceVue modifier(long id, AnnonceSaisie s) {
        Annonce a = annonce(id);
        a.verifierVersion(s.version());
        a.renommer(s.titre());
        a.remplacerSlides(slides(s));
        journal.enregistrer(Domaine.ANNONCE, "MODIFICATION", String.valueOf(id),
                "a modifié l'annonce " + guillemets(a.getTitre()));
        return AnnonceVue.de(a);
    }

    @CacheEvict(cacheNames = AnnoncesPubliques.CACHE, allEntries = true)
    public AnnonceVue changerStatut(long id, StatutPublication cible, Long version) {
        Annonce a = annonce(id);
        a.verifierVersion(version);
        StatutPublication avant = a.getStatut();
        if (a.changerStatut(cible)) {
            journal.enregistrer(Domaine.ANNONCE, "STATUT_" + cible, String.valueOf(id),
                    verbeStatut(avant, cible) + " l'annonce " + guillemets(a.getTitre()));
        }
        return AnnonceVue.de(a);
    }

    private Annonce annonce(long id) {
        return annonces.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Annonce introuvable."));
    }

    private static List<Annonce.SlideDonnees> slides(AnnonceSaisie s) {
        return s.slides().stream()
                .map(sl -> new Annonce.SlideDonnees(sl.titre(), sl.message(), sl.imageUrl()))
                .toList();
    }
}
