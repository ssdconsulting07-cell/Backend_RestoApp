package sn.chezketchup.backend.catalogue.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.chezketchup.backend.catalogue.domaine.AccompagnementCondiment;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.commun.publication.StatutPublication;

public interface AccompagnementCondimentRepository extends JpaRepository<AccompagnementCondiment, Long> {

    @Query("""
            select a from AccompagnementCondiment a
            where (:statut is null or a.statut = :statut)
              and (:type is null or a.type = :type)
            order by a.type, a.nom""")
    List<AccompagnementCondiment> rechercher(@Param("statut") StatutPublication statut,
                                             @Param("type") TypeAccompagnement type);
}
