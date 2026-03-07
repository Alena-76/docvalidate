package com.bank.docvalidate.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures Swagger / OpenAPI documentation.
 * Visit: http://localhost:8080/swagger-ui.html after starting the app.
 *
 * The securityScheme tells Swagger UI to show an "Authorize" button
 * where you can paste your JWT token for authenticated requests.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("DocValidate API")
                .description("""
                    Bank document second-level validation system.
                    
                    **Workflow:**
                    1. POST /api/auth/login → get JWT token
                    2. POST /api/documents → submit document for review
                    3. POST /api/documents/{id}/claim → reviewer picks it up
                    4. POST /api/documents/{id}/decision → APPROVE / REJECT / ESCALATE
                    5. POST /api/documents/{id}/resolve-escalation → supervisor resolves
                """.trimIndent())
                .version("1.0.0")
                .contact(Contact().name("Bank IT Team").email("it@bank.com"))
        )
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Enter your JWT token (obtained from /api/auth/login)")
            )
        )
}
