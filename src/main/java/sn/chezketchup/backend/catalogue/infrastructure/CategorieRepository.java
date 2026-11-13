package sn.chezketchup.backend.catalogue.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.commun.publication.StatutPublication;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    List<Categorie> findAllByOrderByNomAsc();

    List<Categorie> findByStatutOrderByNomAsc(StatutPublication statut);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    boolean existsByNomIgnoreCase(String nom);
}
