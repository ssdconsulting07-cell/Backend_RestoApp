package sn.chezketchup.backend.commun.temps;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Heure de reference : Dakar (UTC+0, sans heure d'ete). Decide notamment
 * quel plat du jour est commandable. Injectee pour etre remplacable en test.
 */
@Configuration
public class Horloge {

    public static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    @Bean
    public Clock clock() {
        return Clock.system(DAKAR);
    }
}
