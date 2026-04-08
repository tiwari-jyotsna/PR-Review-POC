package com.loyalty.wallet.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI loyaltyWalletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Loyalty Wallet API")
                        .description("""
                                Ledger-based loyalty points system.

                                All balance computations are derived from an append-only transaction ledger
                                stored in-memory. Thread safety is guaranteed via per-user synchronisation.

                                Pass **X-Request-ID** header on any request to correlate logs end-to-end.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("platform@example.com"))
                        .license(new License()
                                .name("Internal — Not for distribution")))
                .externalDocs(new ExternalDocumentation()
                        .description("Architecture Decision Records")
                        .url("https://confluence.example.com/loyalty-wallet"));
    }
}
