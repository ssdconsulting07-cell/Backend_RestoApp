package sn.chezketchup.backend.commandes.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    /**
     * Verrou exclusif sur la ligne (SELECT ... FOR UPDATE) pour un changement
     * de statut : deux postes qui agissent en meme temps sur la meme
     * commande sont servis l'un apres l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commande c where c.id = :id")
    Optional<Commande> verrouiller(@Param("id") long id);

    @Query("""
            select c from Commande c
            where c.creeLe >= :depuis
              and c.statut in :statuts
              and (:mode is null or c.mode = :mode)
              and (:canal is null or c.canal = :canal)
            order by c.creeLe desc""")
    List<Commande> pourStaff(@Param("depuis") Instant depuis, @Param("statuts") Collection<StatutCommande> statuts,
                             @Param("mode") ModeCommande mode, @Param("canal") Canal canal, Pageable page);

    List<Commande> findTop50ByClientTelephoneAndCreeLeAfterOrderByCreeLeDesc(String telephone, Instant depuis);

    /** Commandes toujours en attente de paiement, creees avant la limite. */
    @Query("select c.id from Commande c where c.statut = sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande.CREEE "
            + "and c.creeLe < :limite order by c.id")
    List<Long> enAttenteDePaiementAvant(@Param("limite") Instant limite, Pageable page);
}
