package sn.chezketchup.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI senYummiesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SenYummies API")
                        .description("API du backend SenYummies (Chez Ketchup) — authentification, produits, commandes, paiements.")
                        .version("v1"));
    }
}
