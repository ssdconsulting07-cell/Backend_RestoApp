package sn.chezketchup.backend.catalogue.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import sn.chezketchup.backend.catalogue.application.Horaires;

/** Horaires des services Dejeuner / Diner : lecture publique, reglage par le Gerant. */
@RestController
@Tag(name = "Catalogue")
public class HorairesController {

    private final Horaires horaires;

    public HorairesController(Horaires horaires) {
        this.horaires = horaires;
    }

    @GetMapping("/horaires-services")
    @Operation(summary = "Horaires du dejeuner et du diner (heure de Dakar ; le diner peut finir apres minuit)")
    public List<Horaires.Vue> lister() {
        return horaires.lister();
    }

    @PutMapping("/horaires-services")
    @PreAuthorize("hasRole('GERANT')")
    @Operation(summary = "Regler les horaires des services (Gerant)")
    public List<Horaires.Vue> modifier(@RequestBody @NotEmpty List<Horaires.@Valid Saisie> saisies) {
        return horaires.modifier(saisies);
    }
}
