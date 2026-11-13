package sn.chezketchup.backend.audit.infrastructure;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.chezketchup.backend.audit.domaine.AuditEvenement;

public interface AuditEvenementRepository extends JpaRepository<AuditEvenement, Long> {

    @Query("""
            select a from AuditEvenement a
            where a.horodatage >= :du and a.horodatage < :au
              and (:role is null or a.role = :role)
              and (:domaine is null or a.domaine = :domaine)
            order by a.horodatage desc, a.id desc""")
    Page<AuditEvenement> rechercher(@Param("du") Instant du, @Param("au") Instant au, @Param("role") String role,
                                    @Param("domaine") String domaine, Pageable page);
}
