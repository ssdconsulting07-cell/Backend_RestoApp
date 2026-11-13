package sn.chezketchup.backend.commun.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import sn.chezketchup.backend.commun.publication.Visibilite;

/**
 * Lectures publiques du catalogue : cache HTTP court (30 s) pour soulager le
 * serveur aux heures de pointe ; le Gerant, lui, voit toujours l'etat reel.
 */
public final class CacheHttp {

    private static final CacheControl PUBLIC = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private CacheHttp() {
    }

    public static <T> ResponseEntity<T> lecture(T corps) {
        return ResponseEntity.ok()
                .cacheControl(Visibilite.estVuePublique() ? PUBLIC : CacheControl.noStore())
                .body(corps);
    }
}
