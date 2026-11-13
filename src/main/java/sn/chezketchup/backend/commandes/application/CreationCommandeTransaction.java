package sn.chezketchup.backend.commandes.application;

import static sn.chezketchup.backend.commun.audit.Libelles.fcfa;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Commande.Destinataire;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.domaine.LigneCommande.LigneTarifee;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.commun.erreurs.RequeteInvalideException;
import sn.chezketchup.backend.commun.idempotence.Idempotence;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;
import sn.chezketchup.backend.paiements.application.PaiementService;
import sn.chezketchup.backend.paiements.application.PaiementService.IssuePaiement;
import sn.chezketchup.backend.security.Role;
import sn.chezketchup.backend.tempsreel.EvenementSortant;
import sn.chezketchup.backend.tempsreel.EvenementSortantRepository;
import sn.chezketchup.backend.zones.domaine.Secteur;
import sn.chezketchup.backend.zones.infrastructure.RegionRepository;
import sn.chezketchup.backend.zones.infrastructure.SecteurRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Creation d'une commande, en UNE transaction : commande + lignes figees +
 * paiement + historique + evenement temps reel (outbox) + audit + reponse
 * memorisee pour l'idempotence. Une erreur a n'importe quelle etape annule
 * tout.
 */
@Component
public class CreationCommandeTransaction {

    private final Tarification tarification;
    private final CommandeRepository commandes;
    private final SecteurRepository secteurs;
    private final RegionRepository regions;
    private final PaiementService paiements;
    private final EvenementSortantRepository evenements;
    private final JournalActions journal;
    private final Idempotence idempotence;
    private final ObjectMapper json;
    private final Clock horloge;

    public CreationCommandeTransaction(Tarification tarification, CommandeRepository commandes,
                                       SecteurRepository secteurs, RegionRepository regions,
                                       PaiementService paiements, EvenementSortantRepository evenements,
                                       JournalActions journal, Idempotence idempotence, ObjectMapper json,
                                       Clock horloge) {
        this.tarification = tarification;
        this.commandes = commandes;
        this.secteurs = secteurs;
        this.regions = regions;
        this.paiements = paiements;
        this.evenements = evenements;
        this.journal = journal;
        this.idempotence = idempotence;
        this.json = json;
        this.horloge = horloge;
    }

    /** Reponse de creation : la commande (contrat : Commande) + le jeton de suivi + le paiement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CommandeCreee(@JsonUnwrapped CommandeVue commande, String jetonSuivi, PaiementInfo paiement) {
    }

    /** redirectUrl : page de paiement du prestataire (null avec le simulateur). */
    public record PaiementInfo(String redirectUrl, boolean simule) {
    }

    @Transactional
    public String executer(CommandeSaisie s, String cleIdempotence) {
        Role role = UtilisateurCourant.role().orElse(null);
        String acteur = UtilisateurCourant.identifiant().orElse(null);
        Instant maintenant = Instant.now(horloge);

        String prenom = texte(s.clientPrenom());
        String telephone = null;
        if (s.canal() == Canal.COMPTOIR) {
            if (role != Role.VENDEUR) {
                throw new AccessDeniedException("La vente au comptoir est reservee au Vendeur.");
            }
            if (s.mode() != ModeCommande.RETRAIT) {
                throw new RequeteInvalideException("MODE_INVALIDE", "Une vente au comptoir est a emporter.", "mode");
            }
            if (s.moyenPaiement() == null) {
                throw new RequeteInvalideException("MOYEN_PAIEMENT_REQUIS", "Choisissez le moyen de paiement.", "moyenPaiement");
            }
            if (texte(s.clientTelephone()) != null) {
                telephone = telephoneValide(s.clientTelephone());
            }
        } else {
            if (prenom == null) {
                throw new RequeteInvalideException("PRENOM_REQUIS", "Indiquez votre prenom.", "clientPrenom");
            }
            telephone = telephoneValide(s.clientTelephone());
            if (s.moyenPaiement() == null || s.moyenPaiement() == MoyenPaiement.ESPECES) {
                throw new RequeteInvalideException("MOYEN_PAIEMENT_INVALIDE",
                        "Une commande en ligne se paie par Wave, Orange Money, Mixx by Yas ou carte.", "moyenPaiement");
            }
        }

        List<LigneTarifee> lignes = tarification.tarifer(s.lignes());
        long frais = 0;
        Destinataire destinataire;
        if (s.mode() == ModeCommande.LIVRAISON) {
            Secteur secteur = secteurLivrable(s.secteurId());
            frais = secteur.getPrixLivraison();
            CommandeSaisie.Position p = s.positionLivraison();
            destinataire = new Destinataire(prenom, texte(s.clientNom()), telephone, secteur.getId(), secteur.getNom(),
                    secteur.getRegionCode(), p == null ? null : p.latitude(), p == null ? null : p.longitude(),
                    p == null ? null : p.precision(), texte(s.note()));
        } else {
            destinataire = new Destinataire(prenom, texte(s.clientNom()), telephone, null, null, null, null, null, null,
                    texte(s.note()));
        }

        long sousTotal = lignes.stream().mapToLong(LigneTarifee::montant).sum();
        verifierMontant(s.sousTotal(), sousTotal, "sousTotal");
        verifierMontant(s.fraisLivraison(), frais, "fraisLivraison");
        verifierMontant(s.total(), sousTotal + frais, "total");
        if (s.canal() == Canal.COMPTOIR && s.moyenPaiement() == MoyenPaiement.ESPECES
                && (s.montantRecu() == null || s.montantRecu() < sousTotal + frais)) {
            throw new RequeteInvalideException("MONTANT_INSUFFISANT",
                    "Le montant recu doit couvrir le total (" + fcfa(sousTotal + frais) + ").", "montantRecu");
        }

        Commande c = Commande.nouvelle(s.canal(), s.mode(), s.moyenPaiement(), destinataire, lignes, frais,
                s.canal() == Canal.COMPTOIR ? acteur : null, maintenant);
        c = commandes.saveAndFlush(c);

        String jeton = null;
        PaiementInfo paiement = null;
        if (s.canal() == Canal.APP) {
            jeton = JetonSuivi.generer();
            c.attacherJetonSuivi(JetonSuivi.empreinte(jeton));
            IssuePaiement issue = paiements.payerEnLigne(c);
            paiement = new PaiementInfo(issue.redirectUrl(), issue.simule());
        } else {
            paiements.encaisserAuComptoir(c, s.moyenPaiement(), s.montantRecu(), acteur);
        }
        if (c.getStatut() == StatutCommande.PAYEE) {
            evenements.save(new EvenementSortant(EvenementSortant.Type.COMMANDE_PAYEE, c.getId(), maintenant));
        }
        journal.enregistrer(Domaine.COMMANDE, "CREATION", String.valueOf(c.getId()), s.canal() == Canal.COMPTOIR
                ? "a encaissé la commande #" + c.getId() + " (" + fcfa(c.getTotal()) + ")"
                : "nouvelle commande #" + c.getId() + " depuis l'App Client ("
                        + (c.getMode() == ModeCommande.LIVRAISON ? "livraison" : "à emporter") + ")");

        CommandeVue vue = s.canal() == Canal.APP ? CommandeVue.pourClient(c) : CommandeVue.pourStaff(c, Role.VENDEUR);
        String reponse = json.writeValueAsString(new CommandeCreee(vue, jeton, paiement));
        if (cleIdempotence != null) {
            idempotence.memoriser(cleIdempotence, c.getId(), reponse);
        }
        return reponse;
    }

    private Secteur secteurLivrable(String secteurId) {
        if (texte(secteurId) == null) {
            throw new RequeteInvalideException("ZONE_REQUISE", "Choisissez votre zone de livraison.", "secteurId");
        }
        return secteurs.findById(secteurId.trim())
                .filter(s -> !s.isEstMasque())
                .filter(s -> regions.findById(s.getRegionCode()).map(r -> r.isActive()).orElse(false))
                .orElseThrow(() -> new RequeteInvalideException("ZONE_INDISPONIBLE",
                        "Nous ne livrons pas (ou plus) dans cette zone.", "secteurId"));
    }

    private static String telephoneValide(String saisie) {
        return Telephone.normaliser(saisie).orElseThrow(() -> new RequeteInvalideException("TELEPHONE_INVALIDE",
                "Numero senegalais a 9 chiffres (ex. 77 123 45 67).", "clientTelephone"));
    }

    private static void verifierMontant(Long envoye, long calcule, String champ) {
        if (envoye != null && envoye != calcule) {
            throw new RequeteInvalideException("TOTAL_INCOHERENT",
                    "Le montant a change (" + fcfa(calcule) + "). Actualisez votre panier.", champ);
        }
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
