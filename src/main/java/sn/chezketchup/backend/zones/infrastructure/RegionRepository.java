package sn.chezketchup.backend.zones.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.zones.domaine.Region;

public interface RegionRepository extends JpaRepository<Region, String> {

    List<Region> findAllByOrderByNomAsc();

    List<Region> findByActiveOrderByNomAsc(boolean active);
}
