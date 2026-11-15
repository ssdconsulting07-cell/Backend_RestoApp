package sn.chezketchup.backend.catalogue.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.catalogue.domaine.HoraireService;
import sn.chezketchup.backend.catalogue.domaine.Service;

public interface HoraireServiceRepository extends JpaRepository<HoraireService, Service> {
}
