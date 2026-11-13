package sn.chezketchup.backend.paiements.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.paiements.domaine.NotificationRecue;

public interface NotificationRecueRepository extends JpaRepository<NotificationRecue, Long> {

    boolean existsByPrestataireAndEvenementId(String prestataire, String evenementId);
}
