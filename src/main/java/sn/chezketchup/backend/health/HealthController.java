package sn.chezketchup.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(
        name = "Sante",
        description = "Sonde de connectivite legere (aucun acces base de donnees), utilisee par les "
                + "frontends (App Client, SenYummies Manager) pour detecter une coupure reseau sans "
                + "solliciter un endpoint metier a chaque verification."
)
public class HealthController {

    @GetMapping
    @Operation(
            summary = "Sonde de disponibilite",
            description = "Repond 200 immediatement, sans lecture base de donnees. Public (pas d'authentification)."
    )
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
