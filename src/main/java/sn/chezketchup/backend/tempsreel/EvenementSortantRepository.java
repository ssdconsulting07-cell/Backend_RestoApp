package sn.chezketchup.backend.tempsreel;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvenementSortantRepository extends JpaRepository<EvenementSortant, Long> {

    /**
     * Prochains evenements a publier. SKIP LOCKED : si un jour plusieurs
     * instances relaient en parallele, chacune prend des lignes differentes.
     */
    @Query(value = "SELECT * FROM evenements_sortants WHERE publie_le IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<EvenementSortant> aPublier();

    /** Rattrapage apres une reconnexion (en-tete Last-Event-ID). */
    @Query("select e from EvenementSortant e where e.id > :dernier and e.publieLe is not null and e.creeLe > :depuis order by e.id")
    List<EvenementSortant> publiesApres(@Param("dernier") long dernier, @Param("depuis") Instant depuis);
}
