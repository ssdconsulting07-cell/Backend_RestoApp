package sn.chezketchup.backend.commandes.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import sn.chezketchup.backend.catalogue.domaine.AccompagnementCondiment;
import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.domaine.PlatDuJour;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.catalogue.domaine.TypeAccompagnement;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;
import sn.chezketchup.backend.commandes.domaine.LigneCommande.LigneTarifee;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.publication.StatutPublication;

/**
 * Recalcul complet des lignes depuis le catalogue PUBLIE, au moment de la
 * commande : seul le serveur fixe les prix. Une valeur envoyee par le client
 * (prix unitaire, prix d'accompagnement) est seulement comparee : si elle
 * differe, refus 400 sur le champ concerne (le client affichait un prix
 * perime), jamais prise telle quelle.
 *
 * Rupture de stock pendant qu'un client commande : la disponibilite est lue
 * dans la transaction de creation ; une commande validee avant le passage en
 * rupture reste valide (il n'y a pas de quantite en stock a decrementer, la
 * cuisine peut annuler une commande PAYEE).
 */
@Component
public class Tarification {

    private final ProduitRepository produits;
    private final CategorieRepository categories;
    private final PlatDuJourRepository plats;
    private final AccompagnementCondimentRepository accompagnements;
    private final Clock horloge;

    public Tarification(ProduitRepository produits, CategorieRepository categories, PlatDuJourRepository plats,
                        AccompagnementCondimentRepository accompagnements, Clock horloge) {
        this.produits = produits;
        this.categories = categories;
        this.plats = plats;
        this.accompagnements = accompagnements;
        this.horloge = horloge;
    }

    public List<LigneTarifee> tarifer(List<CommandeSaisie.Ligne> lignes) {
        List<LigneTarifee> resultat = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            CommandeSaisie.Ligne l = lignes.get(i);
            String champ = "lignes[" + i + "]";
            LigneTarifee t = (l.type() == TypeLigne.PLAT_DU_JOUR) ? platDuJour(l, champ) : produit(l, champ);
            if (l.prixUnitaire() != null && l.prixUnitaire() != t.prixUnitaire()) {
                throw prixIncoherent(champ + ".prixUnitaire", t.nom());
            }
            resultat.add(t);
        }
        return resultat;
    }

    private LigneTarifee produit(CommandeSaisie.Ligne l, String champ) {
        long id = identifiant(l.produitId(), champ + ".produitId");
        Produit p = produits.findById(id)
                .filter(x -> x.getStatut() == StatutPublication.ACTIF)
                .orElseThrow(() -> indisponible(champ + ".produitId", "Ce produit n'est plus proposé."));
        Categorie c = categories.findById(p.getCategorieId())
                .filter(x -> x.getStatut() == StatutPublication.ACTIF)
                .orElseThrow(() -> indisponible(champ + ".produitId", "Ce produit n'est plus proposé."));
        if (p.getDisponibilite() == Disponibilite.RUPTURE) {
            throw indisponible(champ + ".produitId", p.getNom() + " est en rupture de stock.");
        }
        boolean accompagnementDemande = present(l.accompagnementId()) || present(l.accompagnementNom());
        if (!accompagnementDemande) {
            return new LigneTarifee(TypeLigne.PRODUIT, id, p.getNom(), p.getPrix(), l.quantite(), null, null, null);
        }
        if (!c.isAccompagnementPropose()) {
            throw new RequeteInvalideException("ACCOMPAGNEMENT_NON_PROPOSE",
                    p.getNom() + " ne se commande pas avec un accompagnement.", champ + ".accompagnementId");
        }
        AccompagnementCondiment a = accompagnement(l, champ);
        if (l.accompagnementPrix() != null && l.accompagnementPrix() != a.getPrix()) {
            throw prixIncoherent(champ + ".accompagnementPrix", a.getNom());
        }
        return new LigneTarifee(TypeLigne.PRODUIT, id, p.getNom(), p.getPrix() + a.getPrix(), l.quantite(),
                a.getId(), a.getNom(), a.getPrix());
    }

    /**
     * Par identifiant (contrat a venir, ecart 3.2) ou, en attendant que l'App
     * Client l'envoie, par nom parmi les accompagnements publies.
     */
    private AccompagnementCondiment accompagnement(CommandeSaisie.Ligne l, String champ) {
        List<AccompagnementCondiment> publies = accompagnements.rechercher(StatutPublication.ACTIF,
                TypeAccompagnement.ACCOMPAGNEMENT);
        if (present(l.accompagnementId())) {
            long aid = identifiant(l.accompagnementId(), champ + ".accompagnementId");
            return publies.stream().filter(a -> Objects.equals(a.getId(), aid)).findFirst()
                    .orElseThrow(() -> indisponible(champ + ".accompagnementId", "Cet accompagnement n'est plus proposé."));
        }
        String nom = l.accompagnementNom().trim();
        return publies.stream().filter(a -> a.getNom().equalsIgnoreCase(nom)).findFirst()
                .orElseThrow(() -> indisponible(champ + ".accompagnementNom", "Cet accompagnement n'est plus proposé."));
    }

    private LigneTarifee platDuJour(CommandeSaisie.Ligne l, String champ) {
        long id = identifiant(l.produitId(), champ + ".produitId");
        PlatDuJour p = plats.findById(id)
                .filter(x -> x.getStatut() == StatutPublication.ACTIF)
                .orElseThrow(() -> indisponible(champ + ".produitId", "Ce plat du jour n'est plus proposé."));
        if (p.getJour() != aujourdhui()) {
            throw new RequeteInvalideException("PLAT_INDISPONIBLE",
                    p.getNom() + " n'est pas le plat du jour aujourd'hui.", champ + ".produitId");
        }
        if (present(l.accompagnementId()) || present(l.accompagnementNom())) {
            throw new RequeteInvalideException("ACCOMPAGNEMENT_NON_PROPOSE",
                    "Un plat du jour ne se commande pas avec un accompagnement.", champ + ".accompagnementId");
        }
        return new LigneTarifee(TypeLigne.PLAT_DU_JOUR, id, p.getNom(), p.getPrix(), l.quantite(), null, null, null);
    }

    JourSemaine aujourdhui() {
        return JourSemaine.values()[LocalDate.now(horloge).getDayOfWeek().getValue() - 1];
    }

    private static long identifiant(String id, String champ) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw indisponible(champ, "Article inconnu.");
        }
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static RequeteInvalideException indisponible(String champ, String message) {
        return new RequeteInvalideException("PRODUIT_INDISPONIBLE", message, champ);
    }

    private static RequeteInvalideException prixIncoherent(String champ, String nom) {
        return new RequeteInvalideException("PRIX_INCOHERENT",
                "Le prix de " + nom + " a change. Actualisez votre panier.", champ);
    }
}
