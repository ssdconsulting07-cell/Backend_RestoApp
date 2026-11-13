package sn.chezketchup.backend.paiements.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.paiements.domaine.Paiement;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    Optional<Paiement> findByPrestataireAndReference(String prestataire, String reference);
}
