package sn.chezketchup.backend.avis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.chezketchup.backend.avis.domaine.AvisLivraison;
import sn.chezketchup.backend.avis.infrastructure.AvisLivraisonRepository;
import sn.chezketchup.backend.avis.infrastructure.AvisLivraisonRepository.NoteLivreur;
import sn.chezketchup.backend.commandes.application.JetonSuivi;
import sn.chezketchup.backend.commandes.domaine.ChangementStatut;
import sn.chezketchup.backend.commandes.domaine.Commande;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.StatutCommande;
import sn.chezketchup.backend.commandes.infrastructure.CommandeRepository;
import sn.chezketchup.backend.commun.audit.JournalActions;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.erreurs.RessourceIntrouvableException;

/**
 * Avis du client sur sa livraison : note de 1 a 5 et commentaire facultatif.
 *
 * - Seul le client qui a passe la commande peut noter (jeton de suivi ; sans
 *   lui : 404, on ne confirme pas que la commande existe).
 * - Seulement une commande en LIVRAISON deja LIVREE (409 COMMANDE_NON_LIVREE).
 * - Un seul avis par commande (409 AVIS_DEJA_DONNE), garanti aussi par la base.
 * - Le livreur note est celui qui a valide la livraison (historique LIVREE).
 * - L'avis est trace dans le journal d'audit, dans la meme transaction.
 */
@Service
public class AvisLivraisonService {

    private final AvisLivraisonRepository avis;
    private final CommandeRepository commandes;
    private final JournalActions journal;
    private final Clock horloge;

    public AvisLivraisonService(AvisLivraisonRepository avis, CommandeRepository commandes, JournalActions journal,
                                Clock horloge) {
        this.avis = avis;
        this.commandes = commandes;
        this.journal = journal;
        this.horloge = horloge;
    }

    public record Saisie(@NotNull @Min(AvisLivraison.NOTE_MIN) @Max(AvisLivraison.NOTE_MAX) Integer note,
                         @Size(max = 300) String commentaire) {
    }

    public record Vue(String commandeId, int note, String commentaire, Instant creeLe) {
        static Vue de(AvisLivraison a) {
            return new Vue(String.valueOf(a.getCommandeId()), a.getNote(), a.getCommentaire(), a.getCreeLe());
        }
    }

    /** Note moyenne (une decimale) et nombre d'avis d'un livreur. */
    public record NoteMoyenne(double moyenne, long nombre) {
    }

    @Transactional
    public Vue donner(long commandeId, String jeton, Saisie saisie) {
        Commande c = commandeDuClient(commandeId, jeton);
        if (c.getMode() != ModeCommande.LIVRAISON || c.getStatut() != StatutCommande.LIVREE) {
            throw new ConflitException("COMMANDE_NON_LIVREE", "La livraison pourra être notée une fois la commande livrée.");
        }
        if (avis.existsByCommandeId(c.getId())) {
            throw new ConflitException("AVIS_DEJA_DONNE", "Vous avez déjà noté cette livraison. Merci !");
        }
        String livreur = c.getHistorique().stream()
                .filter(h -> h.getStatut() == StatutCommande.LIVREE)
                .max(Comparator.comparing(ChangementStatut::getHorodatage))
                .map(ChangementStatut::getActeur)
                .orElse(null);
        AvisLivraison cree;
        try {
            cree = avis.saveAndFlush(new AvisLivraison(c.getId(), livreur, saisie.note(), saisie.commentaire(),
                    Instant.now(horloge)));
        } catch (DataIntegrityViolationException e) {
            // Deux envois simultanes : la contrainte d'unicite tranche.
            throw new ConflitException("AVIS_DEJA_DONNE", "Vous avez déjà noté cette livraison. Merci !");
        }
        journal.enregistrerEnTantQue("client", JournalActions.Domaine.COMMANDE, "AVIS", String.valueOf(c.getId()),
                "a noté la livraison de la commande #" + c.getId() + " : " + saisie.note() + "/5"
                        + (livreur != null ? " (livreur " + livreur + ")" : ""));
        return Vue.de(cree);
    }

    @Transactional(readOnly = true)
    public Vue consulter(long commandeId, String jeton) {
        Commande c = commandeDuClient(commandeId, jeton);
        return avis.findByCommandeId(c.getId()).map(Vue::de)
                .orElseThrow(() -> new RessourceIntrouvableException("Pas encore d'avis pour cette commande."));
    }

    /** Moyennes par identifiant de livreur (pour la gestion du personnel). */
    @Transactional(readOnly = true)
    public Map<String, NoteMoyenne> notesParLivreur() {
        return avis.notesParLivreur().stream().collect(Collectors.toMap(NoteLivreur::getLivreur,
                n -> new NoteMoyenne(Math.round(n.getMoyenne() * 10) / 10.0, n.getNombre()),
                (a, b) -> a));
    }

    private Commande commandeDuClient(long commandeId, String jeton) {
        return commandes.findById(commandeId)
                .filter(c -> JetonSuivi.correspond(jeton, c.getJetonSuiviEmpreinte()))
                .orElseThrow(() -> new RessourceIntrouvableException("Commande introuvable."));
    }
}
