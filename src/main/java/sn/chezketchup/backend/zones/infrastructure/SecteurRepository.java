package sn.chezketchup.backend.zones.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.zones.domaine.Secteur;

public interface SecteurRepository extends JpaRepository<Secteur, String> {

    List<Secteur> findByRegionCodeOrderByDepartementAscNomAsc(String regionCode);

    List<Secteur> findByRegionCodeAndEstMasqueFalseOrderByDepartementAscNomAsc(String regionCode);

    boolean existsByRegionCodeAndNomIgnoreCase(String regionCode, String nom);
}
