package sn.chezketchup.backend.annonces.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.chezketchup.backend.annonces.domaine.Annonce;
import sn.chezketchup.backend.commun.publication.StatutPublication;

public interface AnnonceRepository extends JpaRepository<Annonce, Long> {

    /** Slides chargees dans la meme requete (pas de N+1). */
    @EntityGraph(attributePaths = "slides")
    @Query("select a from Annonce a where (:statut is null or a.statut = :statut) order by a.modifieLe desc")
    List<Annonce> rechercher(@Param("statut") StatutPublication statut);
}
