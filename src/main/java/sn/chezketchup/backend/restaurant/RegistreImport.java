package sn.chezketchup.backend.restaurant;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Memoire de l'import (tables import_restaurant_*, migration V9) : ce qui a deja ete applique. */
@Component
public class RegistreImport {

    private final JdbcTemplate jdbc;
    private final Clock horloge;

    public RegistreImport(JdbcTemplate jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    public boolean passageApplique(String jeton) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM import_restaurant_passages WHERE jeton = ?",
                Integer.class, jeton);
        return n != null && n > 0;
    }

    public void enregistrerPassage(String jeton, String restaurant, boolean forcer, String bilan) {
        jdbc.update("INSERT INTO import_restaurant_passages (jeton, restaurant, forcer, bilan, applique_le) "
                        + "VALUES (?, ?, ?, ?, ?)", jeton, restaurant, forcer,
                bilan.length() > 500 ? bilan.substring(0, 500) : bilan, maintenant());
    }

    public Optional<String> ressource(String entite, String cle) {
        List<String> ids = jdbc.queryForList(
                "SELECT ressource_id FROM import_restaurant_fiches WHERE entite = ? AND cle = ?",
                String.class, entite, cle);
        return ids.stream().findFirst();
    }

    public void memoriser(String entite, String cle, String ressourceId) {
        jdbc.update("INSERT INTO import_restaurant_fiches (entite, cle, ressource_id, importe_le) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE ressource_id = VALUES(ressource_id), importe_le = VALUES(importe_le)",
                entite, cle, ressourceId, maintenant());
    }

    private Timestamp maintenant() {
        return Timestamp.from(Instant.now(horloge));
    }
}
