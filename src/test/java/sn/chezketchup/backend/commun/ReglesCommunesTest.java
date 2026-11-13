package sn.chezketchup.backend.commun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sn.chezketchup.backend.commun.publication.StatutPublication.ACTIF;
import static sn.chezketchup.backend.commun.publication.StatutPublication.ARCHIVE;
import static sn.chezketchup.backend.commun.publication.StatutPublication.BROUILLON;

import org.junit.jupiter.api.Test;

import sn.chezketchup.backend.catalogue.domaine.Categorie;
import sn.chezketchup.backend.commun.audit.Libelles;
import sn.chezketchup.backend.commun.erreurs.ConflitException;
import sn.chezketchup.backend.commun.validation.UrlImageValidator;
import sn.chezketchup.backend.photos.domaine.FormatImage;
import sn.chezketchup.backend.zones.domaine.Secteur;

class ReglesCommunesTest {

    @Test
    void cycleDePublication() {
        assertThat(BROUILLON.peutPasserA(ACTIF)).isTrue();
        assertThat(ACTIF.peutPasserA(BROUILLON)).as("depublier").isTrue();
        assertThat(ACTIF.peutPasserA(ARCHIVE)).isTrue();
        assertThat(ARCHIVE.peutPasserA(BROUILLON)).as("restaurer").isTrue();
        assertThat(ARCHIVE.peutPasserA(ACTIF)).as("pas de republication directe d'une archive").isFalse();
        assertThat(ACTIF.peutPasserA(ACTIF)).as("requete rejouee").isTrue();
    }

    @Test
    void unChangementDeStatutInterditEstRefuseEtUnRejeuNeChangeRien() {
        Categorie c = new Categorie("Burgers", true);
        assertThat(c.changerStatut(ACTIF)).isTrue();
        assertThat(c.changerStatut(ACTIF)).isFalse();
        c.changerStatut(ARCHIVE);
        assertThatThrownBy(() -> c.changerStatut(ACTIF)).isInstanceOf(ConflitException.class);
    }

    @Test
    void versionLueDifferenteDeLaVersionCouranteEstUnConflit() {
        Categorie c = new Categorie("Boissons", false);
        c.verifierVersion(null);
        c.verifierVersion(0L);
        assertThatThrownBy(() -> c.verifierVersion(3L))
                .isInstanceOf(ConflitException.class)
                .extracting(e -> ((ConflitException) e).getCode()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void adressesDImageAcceptees() {
        assertThat(UrlImageValidator.estValide(null)).isTrue();
        assertThat(UrlImageValidator.estValide("")).isTrue();
        assertThat(UrlImageValidator.estValide("https://images.unsplash.com/photo-1?w=800")).isTrue();
        assertThat(UrlImageValidator.estValide("/api/v1/photos/0f8fad5b-d9cb-469f-a165-70867728950e.jpg")).isTrue();
        assertThat(UrlImageValidator.estValide("javascript:alert(1)")).isFalse();
        assertThat(UrlImageValidator.estValide("data:image/png;base64,AAAA")).isFalse();
        assertThat(UrlImageValidator.estValide("http://non-chiffre.example/a.jpg")).isFalse();
        assertThat(UrlImageValidator.estValide("/api/v1/photos/../../etc/passwd")).isFalse();
        assertThat(UrlImageValidator.estValide("https://a.b/" + "x".repeat(600))).isFalse();
    }

    @Test
    void identifiantDeZoneIdentiqueAuxDonneesDuManager() {
        assertThat(Secteur.identifiant("DAKAR", "Plateau/Gorée")).isEqualTo("DAKAR-PLATEAU-GOREE");
        assertThat(Secteur.identifiant("THIES", "  Thiès Nord ")).isEqualTo("THIES-THIES-NORD");
    }

    @Test
    void zoneManuelleModifiableEtSupprimable() {
        Secteur s = Secteur.manuel("DAKAR", "Dakar", "Ouakam", 1500);
        s.modifier(null, "Ouakam Village", 2000);
        assertThat(s.getNom()).isEqualTo("Ouakam Village");
        assertThat(s.getPrixLivraison()).isEqualTo(2000);
        s.verifierSuppressible();
    }

    @Test
    void montantsAuFormatDuManager() {
        assertThat(Libelles.fcfa(3500)).isEqualTo("3 500 F CFA");
        assertThat(Libelles.fcfa(15200)).isEqualTo("15 200 F CFA");
        assertThat(Libelles.fcfa(0)).isEqualTo("0 F CFA");
    }

    @Test
    void signaturesDeFichiers() {
        assertThat(FormatImage.detecter(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}))
                .hasValue(FormatImage.PNG);
        assertThat(FormatImage.detecter("GIF89a......".getBytes())).isEmpty();
        assertThat(FormatImage.detecter(new byte[3])).isEmpty();
    }
}
