package sn.chezketchup.backend.zones.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;
import static sn.chezketchup.backend.commun.audit.Libelles.guillemets;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;
import sn.chezketchup.backend.commun.publication.Visibilite;
import sn.chezketchup.backend.commun.transaction.LectureSeule;
import sn.chezketchup.backend.zones.application.ZonesVues.RegionVue;
import sn.chezketchup.backend.zones.application.ZonesVues.SecteurVue;
import sn.chezketchup.backend.zones.domaine.Region;
import sn.chezketchup.backend.zones.domaine.Secteur;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;

/** Zones de livraison : lecture publique, gestion par le Gerant. */
@Service
@Transactional
public class ZonesService {

    private final RegionRepository regions;
    private final SecteurRepository secteurs;
    private final ZonesPubliques publiques;
    private final JournalActions journal;
    private final LectureSeule lecture;

    public ZonesService(RegionRepository regions, SecteurRepository secteurs, ZonesPubliques publiques,
                        JournalActions journal, LectureSeule lecture) {
        this.regions = regions;
        this.secteurs = secteurs;
        this.publiques = publiques;
        this.journal = journal;
        this.lecture = lecture;
    }

    /** Servi par le cache, sans transaction (ZonesPubliques ouvre la sienne au remplissage). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<RegionVue> regions(Boolean active) {
        return publiques.regions(active);
    }

    /** Client : zones proposees (region ouverte, zones non masquees). Gerant : toutes les zones de la region. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SecteurVue> secteurs(String regionCode) {
        if (Visibilite.estVuePublique()) {
            return publiques.secteurs(regionCode);
        }
        return lecture.executer(() -> {
            region(regionCode);
            return secteurs.findByRegionCodeOrderByDepartementAscNomAsc(regionCode).stream()
                    .map(SecteurVue::de).toList();
        });
    }

    @CacheEvict(cacheNames = {ZonesPubliques.REGIONS, ZonesPubliques.SECTEURS}, allEntries = true)
    public RegionVue activerRegion(String code, ZonesVues.ActivationRegion s) {
        Region r = region(code);
        boolean avant = r.isActive();
        r.activer(s.active(), s.version());
        if (avant != r.isActive()) {
            journal.enregistrer(Domaine.ZONE, r.isActive() ? "REGION_OUVERTE" : "REGION_FERMEE", code,
                    (r.isActive() ? "a ouvert la livraison dans la région " : "a fermé la livraison dans la région ")
                            + guillemets(r.getNom()));
        }
        return RegionVue.de(r);
    }

    @CacheEvict(cacheNames = ZonesPubliques.SECTEURS, allEntries = true)
    public SecteurVue ajouterSecteur(String regionCode, ZonesVues.NouveauSecteur s) {
        region(regionCode);
        Secteur nouveau = Secteur.manuel(regionCode, s.departement(), s.nom(), s.prixLivraison());
        if (secteurs.existsById(nouveau.getId()) || secteurs.existsByRegionCodeAndNomIgnoreCase(regionCode, s.nom().trim())) {
            throw new ConflitException("SECTEUR_EXISTANT", "Une zone porte déjà ce nom dans cette région.");
        }
        Secteur cree = secteurs.save(nouveau);
        journal.enregistrer(Domaine.ZONE, "CREATION", cree.getId(),
                "a ajouté la zone " + guillemets(cree.getNom()) + " (" + fcfa(cree.getPrixLivraison()) + ")");
        return SecteurVue.de(cree);
    }

    @CacheEvict(cacheNames = ZonesPubliques.SECTEURS, allEntries = true)
    public SecteurVue modifierSecteur(String id, ZonesVues.ModificationSecteur s) {
        Secteur secteur = secteur(id);
        secteur.verifierVersion(s.version());
        long ancienPrix = secteur.getPrixLivraison();
        secteur.modifier(s.departement(), s.nom(), s.prixLivraison());
        journal.enregistrer(Domaine.ZONE, "MODIFICATION", id, ancienPrix != secteur.getPrixLivraison()
                ? "a modifié le prix de livraison de " + guillemets(secteur.getNom()) + " : "
                        + fcfa(ancienPrix) + " → " + fcfa(secteur.getPrixLivraison())
                : "a modifié la zone " + guillemets(secteur.getNom()));
        return SecteurVue.de(secteur);
    }

    @CacheEvict(cacheNames = ZonesPubliques.SECTEURS, allEntries = true)
    public SecteurVue masquerSecteur(String id, ZonesVues.MasquageSecteur s) {
        Secteur secteur = secteur(id);
        secteur.verifierVersion(s.version());
        if (secteur.isEstMasque() != s.estMasque()) {
            secteur.masquer(s.estMasque());
            journal.enregistrer(Domaine.ZONE, s.estMasque() ? "MASQUAGE" : "AFFICHAGE", id,
                    (s.estMasque() ? "a masqué la zone " : "a réaffiché la zone ") + guillemets(secteur.getNom()));
        }
        return SecteurVue.de(secteur);
    }

    /** Zone ajoutee a la main uniquement ; un arrondissement officiel se masque (409 sinon). */
    @CacheEvict(cacheNames = ZonesPubliques.SECTEURS, allEntries = true)
    public void supprimerSecteur(String id) {
        Secteur secteur = secteur(id);
        secteur.verifierSuppressible();
        secteurs.delete(secteur);
        journal.enregistrer(Domaine.ZONE, "SUPPRESSION", id, "a supprimé la zone " + guillemets(secteur.getNom()));
    }

    private Region region(String code) {
        return regions.findById(code).orElseThrow(() -> new RessourceIntrouvableException("Région introuvable."));
    }

    private Secteur secteur(String id) {
        return secteurs.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Zone introuvable."));
    }
}
