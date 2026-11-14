package sn.chezketchup.backend.config;

import java.util.Collection;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Profil loadtest UNIQUEMENT : compte les acces au cache du catalogue (succes
 * / echecs) dans la metrique standard cache.gets{cache, result=hit|miss}.
 * Spring Boot ne publie pas ces compteurs pour le cache memoire simple
 * (ConcurrentMapCacheManager) ; ils mesurent l'efficacite du cache pendant
 * les tests de charge (load-tests/lecture-menu.js).
 */
@Configuration
@Profile("loadtest")
public class MetriquesCacheLoadtest {

    @Bean
    static BeanPostProcessor compteurDeCache(ObjectProvider<MeterRegistry> registre) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nom) {
                return bean instanceof CacheManager gestionnaire ? new GestionnaireCompte(gestionnaire, registre) : bean;
            }
        };
    }

    record GestionnaireCompte(CacheManager cible, ObjectProvider<MeterRegistry> registre) implements CacheManager {
        @Override
        public Cache getCache(String nom) {
            Cache cache = cible.getCache(nom);
            return cache == null ? null : new CacheCompte(cache, registre.getObject());
        }

        @Override
        public Collection<String> getCacheNames() {
            return cible.getCacheNames();
        }
    }

    static final class CacheCompte implements Cache {
        private final Cache cible;
        private final Counter succes;
        private final Counter echecs;

        CacheCompte(Cache cible, MeterRegistry registre) {
            this.cible = cible;
            this.succes = Counter.builder("cache.gets").tag("cache", cible.getName()).tag("result", "hit").register(registre);
            this.echecs = Counter.builder("cache.gets").tag("cache", cible.getName()).tag("result", "miss").register(registre);
        }

        private <T> T compter(T valeur) {
            (valeur != null ? succes : echecs).increment();
            return valeur;
        }

        @Override
        public String getName() {
            return cible.getName();
        }

        @Override
        public Object getNativeCache() {
            return cible.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object cle) {
            return compter(cible.get(cle));
        }

        @Override
        public <T> T get(Object cle, Class<T> type) {
            return compter(cible.get(cle, type));
        }

        @Override
        public <T> T get(Object cle, Callable<T> chargeur) {
            boolean present = cible.get(cle) != null;
            (present ? succes : echecs).increment();
            return cible.get(cle, chargeur);
        }

        @Override
        public void put(Object cle, Object valeur) {
            cible.put(cle, valeur);
        }

        @Override
        public void evict(Object cle) {
            cible.evict(cle);
        }

        @Override
        public void clear() {
            cible.clear();
        }
    }
}
