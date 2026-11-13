package sn.chezketchup.backend.commandes.application;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import sn.chezketchup.backend.commandes.domaine.Enums.Canal;
import sn.chezketchup.backend.commandes.domaine.Enums.ModeCommande;
import sn.chezketchup.backend.commandes.domaine.Enums.TypeLigne;
import sn.chezketchup.backend.commandes.domaine.MoyenPaiement;

/**
 * Corps de POST /commandes (contrat : CommandeCreation), tel que l'envoie
 * l'App Client aujourd'hui.
 *
 * Valeurs du client SEULEMENT CONTROLEES, jamais reprises : prix, totaux
 * (refus 400 si elles different du calcul du serveur). Valeurs IGNOREES :
 * statut, statutPaiement, creeLe, regionLivraison, adresseLivraison (le
 * serveur les fixe lui-meme).
 */
public record CommandeSaisie(
        @NotNull Canal canal,
        @NotNull ModeCommande mode,
        MoyenPaiement moyenPaiement,
        @Size(max = 60) String clientPrenom,
        @Size(max = 60) String clientNom,
        @Size(max = 30) String clientTelephone,
        @Size(max = 80) String secteurId,
        @Valid Position positionLivraison,
        @Size(max = 300) String note,
        @NotEmpty @Size(max = 50) List<@Valid Ligne> lignes,
        @PositiveOrZero Long sousTotal,
        @PositiveOrZero Long fraisLivraison,
        @PositiveOrZero Long total,
        @PositiveOrZero Long montantRecu,
        // Envoyes par l'App Client actuelle, ignores par le serveur :
        String statut,
        String statutPaiement,
        String creeLe,
        String regionLivraison,
        String adresseLivraison) {

    public record Ligne(
            @NotBlank @Size(max = 30) String produitId,
            TypeLigne type,
            @NotNull @Min(1) @Max(50) Integer quantite,
            @Size(max = 30) String accompagnementId,
            @Size(max = 80) String accompagnementNom,
            @PositiveOrZero Long accompagnementPrix,
            @PositiveOrZero Long prixUnitaire,
            @Size(max = 80) String nom,
            @Size(max = 200) String personnalisation) {
    }

    public record Position(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @PositiveOrZero @Max(100_000) Integer precision) {
    }
}
