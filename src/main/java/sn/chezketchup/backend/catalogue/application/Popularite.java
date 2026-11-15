package sn.chezketchup.backend.catalogue.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.diffusion.ChangementPublic;

/**
 * Badge Tendance automatique : chaque heure, les produits les plus commandes
 * des 30 derniers jours (commandes payees, annulees exclues) recoivent le
 * badge, sauf si le Gerant l'a force (Oui / Non). La quantite vendue sert
 * aussi d'ordre de popularite dans l'App Client.
 */
@Component
public class Popularite {

    static final Duration PERIODE = Duration.ofDays(30);
    /** Nombre de produits « tendance » automatiques. */
    static final int NOMBRE_TENDANCES = 6;

    private final EntityManager em;
    private final ProduitRepository produits;
    private final Clock horloge;
    private final ApplicationEventPublisher evenements;

    public Popularite(EntityManager em, ProduitRepository produits, Clock horloge,
                      ApplicationEventPublisher evenements) {
        this.em = em;
        this.produits = produits;
        this.horloge = horloge;
        this.evenements = evenements;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    @Transactional
    @CacheEvict(cacheNames = CatalogueCachePublic.PRODUITS, allEntries = true)
    public void recalculer() {
        Instant depuis = Instant.now(horloge).minus(PERIODE);
        @SuppressWarnings("unchecked")
        List<Object[]> lignes = em.createQuery("""
                select l.produitId, sum(l.quantite) from LigneCommande l
                where l.type = sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne.PRODUIT
                  and l.commande.creeLe >= :depuis
                  and l.commande.statut not in (sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.CREEE,
                                               sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.ANNULEE)
                group by l.produitId""").setParameter("depuis", depuis).getResultList();
        Map<Long, Integer> ventes = new HashMap<>();
        for (Object[] l : lignes) {
            ventes.put((Long) l[0], ((Number) l[1]).intValue());
        }
        List<Produit> tous = produits.findAll();
        List<Long> top = tous.stream()
                .filter(p -> ventes.getOrDefault(p.getId(), 0) > 0)
                .sorted((a, b) -> Integer.compare(ventes.getOrDefault(b.getId(), 0), ventes.getOrDefault(a.getId(), 0)))
                .limit(NOMBRE_TENDANCES)
                .map(Produit::getId)
                .toList();
        boolean change = false;
        for (Produit p : tous) {
            change |= p.appliquerPopularite(ventes.getOrDefault(p.getId(), 0), top.contains(p.getId()));
        }
        if (change) {
            evenements.publishEvent(new ChangementPublic(ChangementPublic.Sujet.CATALOGUE));
        }
    }
}
