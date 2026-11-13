package sn.chezketchup.backend.commun.idempotence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CleIdempotenceRepository extends JpaRepository<CleIdempotence, String> {

    @Modifying
    @Query("delete from CleIdempotence c where c.creeLe < :limite")
    int purgerAvant(@Param("limite") Instant limite);
}
