package sn.chezketchup.backend.avis.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sn.chezketchup.backend.avis.domaine.AvisLivraison;

public interface AvisLivraisonRepository extends JpaRepository<AvisLivraison, Long> {

    Optional<AvisLivraison> findByCommandeId(Long commandeId);

    boolean existsByCommandeId(Long commandeId);

    /** Note moyenne et nombre d'avis par livreur. */
    @Query("select a.livreur as livreur, avg(a.note) as moyenne, count(a) as nombre "
            + "from AvisLivraison a where a.livreur is not null group by a.livreur")
    List<NoteLivreur> notesParLivreur();

    interface NoteLivreur {
        String getLivreur();

        Double getMoyenne();

        Long getNombre();
    }
}
