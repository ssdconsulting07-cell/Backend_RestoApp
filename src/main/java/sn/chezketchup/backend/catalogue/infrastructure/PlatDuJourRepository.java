package sn.chezketchup.backend.catalogue.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.PlatDuJour;
import sn.chezketchup.backend.commun.publication.StatutPublication;

public interface PlatDuJourRepository extends JpaRepository<PlatDuJour, Long> {

    @Query("""
            select p from PlatDuJour p
            where (:statut is null or p.statut = :statut)
              and (:jour is null or p.jour = :jour)
            order by p.jour, p.nom""")
    List<PlatDuJour> rechercher(@Param("statut") StatutPublication statut, @Param("jour") JourSemaine jour);
}
