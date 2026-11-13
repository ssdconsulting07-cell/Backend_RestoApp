package sn.chezketchup.backend.audit.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import sn.chezketchup.backend.audit.application.ConsultationAudit;
import sn.chezketchup.backend.audit.application.ConsultationAudit.Filtres;
import sn.chezketchup.backend.audit.application.ConsultationAudit.PageVue;
import sn.chezketchup.backend.commun.audit.JournalActions.Domaine;
import sn.chezketchup.backend.security.Role;

/** Journal d'audit, en lecture seule, reserve au Manager (comme l'ecran Journal d'audit). */
@RestController
@RequestMapping("/audit")
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Journal d'audit")
public class AuditController {

    private final ConsultationAudit audit;

    public AuditController(ConsultationAudit audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Entrees du journal (plus recentes d'abord), filtrables par role, type et periode (7 jours par defaut)")
    public ResponseEntity<PageVue> consulter(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Domaine domaine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int taille) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(audit.consulter(new Filtres(du, au, role, domaine), page, taille));
    }

    @GetMapping("/export.csv")
    @Operation(summary = "Export CSV (Excel) du journal filtre ; l'export est lui-meme trace")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Domaine domaine,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        Filtres f = new Filtres(du, au, role, domaine);
        String nom = "journal-audit-" + (du == null ? "7-derniers-jours" : du + "_" + (au == null ? "aujourdhui" : au)) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(audit.exporterCsv(f));
    }
}
