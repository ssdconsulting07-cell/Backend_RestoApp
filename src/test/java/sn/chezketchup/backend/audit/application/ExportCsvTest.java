package sn.chezketchup.backend.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cellules CSV : echappement et neutralisation des formules (injection CSV / Excel). */
class ExportCsvTest {

    @Test
    void lesFormulesSontNeutralisees() {
        assertThat(ConsultationAudit.cellule("=HYPERLINK(\"http://pirate\",\"clic\")")).startsWith("\"'=HYPERLINK(");
        assertThat(ConsultationAudit.cellule("+221771234567")).isEqualTo("'+221771234567");
        assertThat(ConsultationAudit.cellule("-2+3")).isEqualTo("'-2+3");
        assertThat(ConsultationAudit.cellule("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void lesSeparateursEtGuillemetsSontEchappes() {
        assertThat(ConsultationAudit.cellule("a; b")).isEqualTo("\"a; b\"");
        assertThat(ConsultationAudit.cellule("dit \"bonjour\"")).isEqualTo("\"dit \"\"bonjour\"\"\"");
        assertThat(ConsultationAudit.cellule("a créé la catégorie « Burgers »")).isEqualTo("a créé la catégorie « Burgers »");
        assertThat(ConsultationAudit.cellule(null)).isEmpty();
    }
}
