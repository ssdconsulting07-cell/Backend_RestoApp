package sn.chezketchup.backend.commandes.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import sn.chezketchup.backend.commun.idempotence.Idempotence;
import sn.chezketchup.backend.commun.securite.UtilisateurCourant;
import tools.jackson.databind.ObjectMapper;

/**
 * Point d'entree de la creation : gere l'en-tete Idempotency-Key autour de
 * la transaction de creation.
 *
 * - Sans cle : creation simple (l'App Client actuelle n'envoie pas encore
 *   d'en-tete ; il deviendra obligatoire une fois le Client mis a jour).
 * - Cle deja vue avec la meme requete : la premiere reponse est rejouee
 *   (aucune seconde commande, aucun second paiement).
 * - Echec du traitement : la cle est liberee, un nouvel essai est possible.
 */
@Service
public class CreationCommande {

    private final Idempotence idempotence;
    private final CreationCommandeTransaction transaction;
    private final ObjectMapper json;

    public CreationCommande(Idempotence idempotence, CreationCommandeTransaction transaction, ObjectMapper json) {
        this.idempotence = idempotence;
        this.transaction = transaction;
        this.json = json;
    }

    /** @param corpsJson reponse JSON ; rejouee = vrai si elle provient d'un envoi precedent */
    public record Resultat(String corpsJson, boolean rejouee) {
    }

    public Resultat creer(CommandeSaisie saisie, String cleIdempotence) {
        if (cleIdempotence == null || cleIdempotence.isBlank()) {
            return new Resultat(transaction.executer(saisie, null), false);
        }
        String cle = cleIdempotence.trim();
        String empreinte = Idempotence.empreinte(json.writeValueAsString(saisie)
                + "|" + UtilisateurCourant.identifiant().orElse("client"));
        Optional<String> dejaTraitee = idempotence.reserver(cle, empreinte);
        if (dejaTraitee.isPresent()) {
            return new Resultat(dejaTraitee.get(), true);
        }
        try {
            return new Resultat(transaction.executer(saisie, cle), false);
        } catch (RuntimeException echec) {
            idempotence.liberer(cle);
            throw echec;
        }
    }
}
