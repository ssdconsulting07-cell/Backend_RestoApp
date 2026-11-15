package sn.chezketchup.backend.commandes.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import sn.chezketchup.backend.catalogue.domaine.AccompagnementCondiment;
import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.catalogue.domaine.Disponibilite;
import sn.chezketchup.backend.catalogue.domaine.JourSemaine;
import sn.chezketchup.backend.catalogue.application.Horaires;
import sn.chezketchup.backend.catalogue.domaine.PlatDuJour;
import sn.chezketchup.backend.catalogue.domaine.Service;
import sn.chezketchup.backend.catalogue.domaine.Produit;
import sn.chezketchup.backend.catalogue.infrastructure.AccompagnementCondimentRepository;
import sn.chezketchup.backend.catalogue.infrastructure.CategorieRepository;
import sn.chezketchup.backend.catalogue.infrastructure.PlatDuJourRepository;
import sn.chezketchup.backend.catalogue.infrastructure.ProduitRepository;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;
import sn.chezketchup.backend.commandes.domaine.LigneCommande.AccompagnementFige;
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
    private final Horaires horaires;

    public Tarification(ProduitRepository produits, CategorieRepository categories, PlatDuJourRepository plats,
                        AccompagnementCondimentRepository accompagnements, Clock horloge, Horaires horaires) {
        this.horaires = horaires;
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
        List<AccompagnementFige> choisis = accompagnements(l, champ);
        if (!choisis.isEmpty() && !c.isAccompagnementPropose()) {
            throw new RequeteInvalideException("ACCOMPAGNEMENT_NON_PROPOSE",
                    p.getNom() + " ne se commande pas avec un accompagnement.", champ + ".accompagnementIds");
        }
        return ligne(TypeLigne.PRODUIT, id, p.getNom(), p.getPrix(), l, choisis);
    }

    private LigneTarifee platDuJour(CommandeSaisie.Ligne l, String champ) {
        long id = identifiant(l.produitId(), champ + ".produitId");
        PlatDuJour p = plats.findById(id)
                .filter(x -> x.getStatut() == StatutPublication.ACTIF)
                .orElseThrow(() -> indisponible(champ + ".produitId", "Ce plat du jour n'est plus proposé."));
        Horaires.Creneau creneau = horaires.creneauEnCours().orElseThrow(() -> new RequeteInvalideException(
                "PLAT_HORS_SERVICE", p.getNom() + " se commande seulement pendant le service du déjeuner ou du dîner.",
                champ + ".produitId"));
        if (!p.proposeA(creneau.jour(), creneau.service())) {
            String service = creneau.service() == Service.DEJEUNER ? "ce déjeuner" : "ce dîner";
            throw new RequeteInvalideException("PLAT_INDISPONIBLE",
                    p.getNom() + " n'est pas servi à " + service + ".", champ + ".produitId");
        }
        return ligne(TypeLigne.PLAT_DU_JOUR, id, p.getNom(), p.getPrix(), l, accompagnements(l, champ));
    }

    private static LigneTarifee ligne(TypeLigne type, long id, String nom, long prix, CommandeSaisie.Ligne l,
                                      List<AccompagnementFige> choisis) {
        long supplement = choisis.stream().mapToLong(AccompagnementFige::prix).sum();
        return new LigneTarifee(type, id, nom, prix + supplement, l.quantite(), choisis);
    }

    /**
     * Accompagnements demandes : liste accompagnementIds, ou l'ancien champ
     * unique (identifiant ou nom). Seuls les accompagnements publies sont
     * acceptes, chacun une fois ; un prix envoye different est refuse.
     */
    private List<AccompagnementFige> accompagnements(CommandeSaisie.Ligne l, String champ) {
        List<String> ids = l.accompagnementIds() != null ? l.accompagnementIds() : List.of();
        boolean ancien = ids.isEmpty() && (present(l.accompagnementId()) || present(l.accompagnementNom()));
        if (ids.isEmpty() && !ancien) {
            return List.of();
        }
        // Accompagnements ET condiments publies (l'App Client et la caisse proposent les deux).
        List<AccompagnementCondiment> publies = accompagnements.rechercher(StatutPublication.ACTIF, null);
        List<AccompagnementFige> resultat = new ArrayList<>();
        if (ancien) {
            AccompagnementCondiment a;
            if (present(l.accompagnementId())) {
                long aid = identifiant(l.accompagnementId(), champ + ".accompagnementId");
                a = publies.stream().filter(x -> Objects.equals(x.getId(), aid)).findFirst()
                        .orElseThrow(() -> indisponible(champ + ".accompagnementId", "Cet accompagnement n'est plus proposé."));
            } else {
                String nom = l.accompagnementNom().trim();
                a = publies.stream().filter(x -> x.getNom().equalsIgnoreCase(nom)).findFirst()
                        .orElseThrow(() -> indisponible(champ + ".accompagnementNom", "Cet accompagnement n'est plus proposé."));
            }
            if (l.accompagnementPrix() != null && l.accompagnementPrix() != a.getPrix()) {
                throw prixIncoherent(champ + ".accompagnementPrix", a.getNom());
            }
            resultat.add(new AccompagnementFige(a.getId(), a.getNom(), a.getPrix()));
            return resultat;
        }
        Set<Long> vus = new HashSet<>();
        for (String brut : ids) {
            long aid = identifiant(brut, champ + ".accompagnementIds");
            if (!vus.add(aid)) {
                continue;
            }
            AccompagnementCondiment a = publies.stream().filter(x -> Objects.equals(x.getId(), aid)).findFirst()
                    .orElseThrow(() -> indisponible(champ + ".accompagnementIds", "Un accompagnement choisi n'est plus proposé."));
            resultat.add(new AccompagnementFige(a.getId(), a.getNom(), a.getPrix()));
        }
        return resultat;
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
