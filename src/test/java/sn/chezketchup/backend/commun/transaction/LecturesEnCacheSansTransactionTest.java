package sn.chezketchup.backend.commun.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import sn.chezketchup.backend.annonces.application.AnnonceService;
import sn.chezketchup.backend.annonces.application.AnnoncesPubliques;
import sn.chezketchup.backend.annonces.infrastructure.AnnonceRepository;
import sn.chezketchup.backend.catalogue.application.CatalogueCachePublic;
import sn.chezketchup.backend.catalogue.application.CatalogueService;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.zones.application.ZonesPubliques;
import sn.chezketchup.backend.zones.application.ZonesService;
import sn.chezketchup.backend.zones.domaine.Region;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;

/**
 * Une lecture publique servie par le cache (menu, annonces, zones) n'ouvre
 * AUCUNE transaction : ni connexion empruntee au pool, ni aller-retour MySQL.
 * La transaction de lecture n'est ouverte qu'au remplissage du cache, et pour
 * les lectures du Gerant (etat reel, jamais en cache).
 *
 * Vrais proxies Spring (cache + transactions) ; depots simules ; gestionnaire
 * de transactions qui compte les ouvertures.
 */
@SpringJUnitConfig(LecturesEnCacheSansTransactionTest.Config.class)
class LecturesEnCacheSansTransactionTest {

    static final class CompteurTransactions extends AbstractPlatformTransactionManager {
        final AtomicInteger ouvertes = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ouvertes.incrementAndGet();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus statut) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus statut) {
        }
    }

    @Configuration
    @EnableCaching
    @EnableTransactionManagement
    @Import({LectureSeule.class, CatalogueCachePublic.class, CatalogueService.class, AnnoncesPubliques.class,
            AnnonceService.class, ZonesPubliques.class, ZonesService.class})
    static class Config {
        @Bean
        CompteurTransactions transactionManager() {
            return new CompteurTransactions();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        CategorieRepository categorieRepository() {
            return mock(CategorieRepository.class);
        }

        @Bean
        ProduitRepository produitRepository() {
            return mock(ProduitRepository.class);
        }

        @Bean
        PlatDuJourRepository platDuJourRepository() {
            return mock(PlatDuJourRepository.class);
        }

        @Bean
        AccompagnementCondimentRepository accompagnementRepository() {
            return mock(AccompagnementCondimentRepository.class);
        }

        @Bean
        AnnonceRepository annonceRepository() {
            return mock(AnnonceRepository.class);
        }

        @Bean
        RegionRepository regionRepository() {
            Region dakar = mock(Region.class);
            when(dakar.isActive()).thenReturn(true);
            RegionRepository regions = mock(RegionRepository.class);
            when(regions.findById("DAKAR")).thenReturn(Optional.of(dakar));
            return regions;
        }

        @Bean
        SecteurRepository secteurRepository() {
            return mock(SecteurRepository.class);
        }

        @Bean
        JournalActions journalActions() {
            return mock(JournalActions.class);
        }
    }

    @Autowired
    CompteurTransactions transactions;
    @Autowired
    CacheManager caches;
    @Autowired
    CatalogueService catalogue;
    @Autowired
    AnnonceService annonces;
    @Autowired
    ZonesService zones;

    @BeforeEach
    void cachesVides() {
        caches.getCacheNames().forEach(nom -> caches.getCache(nom).clear());
        transactions.ouvertes.set(0);
    }

    @AfterEach
    void sansUtilisateur() {
        SecurityContextHolder.clearContext();
    }

    private void lireLeMenu() {
        catalogue.categories(null);
        catalogue.produits(null, null);
        catalogue.plats(null, null);
        catalogue.accompagnements(null, null);
        annonces.lister(null);
        zones.regions(null);
        zones.secteurs("DAKAR");
    }

    @Test
    void uneLectureServieParLeCacheNOuvreAucuneTransaction() {
        lireLeMenu();
        assertThat(transactions.ouvertes.get()).as("une transaction par remplissage de cache").isEqualTo(7);

        for (int i = 0; i < 10; i++) {
            lireLeMenu();
        }
        assertThat(transactions.ouvertes.get()).as("aucune transaction de plus : tout vient du cache").isEqualTo(7);
    }

    @Test
    void leGerantLitLEtatReelDansUneTransaction() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "gerant", null, List.of(new SimpleGrantedAuthority("ROLE_GERANT"))));

        catalogue.categories(null);
        catalogue.categories(null);
        annonces.lister(null);
        zones.secteurs("DAKAR");

        assertThat(transactions.ouvertes.get()).as("jamais en cache pour le Gerant").isEqualTo(4);
    }
}
