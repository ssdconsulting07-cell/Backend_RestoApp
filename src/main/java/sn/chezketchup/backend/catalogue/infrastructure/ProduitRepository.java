package sn.chezketchup.backend.catalogue.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.commun.publication.StatutPublication;

public interface ProduitRepository extends JpaRepository<Produit, Long> {

    /**
     * Produits visibles cote client : publies ET dans une categorie publiee
     * (un produit actif d'une categorie en brouillon ne doit pas apparaitre).
     */
    @Query("""
            select p from Produit p
            where p.statut = sn.chezketchup.backend.commun.publication.StatutPublication.ACTIF
              and (:categorieId is null or p.categorieId = :categorieId)
              and exists (select c.id from Categorie c where c.id = p.categorieId
                          and c.statut = sn.chezketchup.backend.commun.publication.StatutPublication.ACTIF)
            order by p.nom""")
    List<Produit> publics(@Param("categorieId") Long categorieId);

    @Query("""
            select p from Produit p
            where (:statut is null or p.statut = :statut)
              and (:categorieId is null or p.categorieId = :categorieId)
            order by p.nom""")
    List<Produit> rechercher(@Param("statut") StatutPublication statut, @Param("categorieId") Long categorieId);

    List<Produit> findByCategorieIdAndStatutNot(Long categorieId, StatutPublication statut);
}
