package it.govpay.console.web;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espone il file {@code openapi.yaml} sorgente come endpoint statico, cosi'
 * SpringDoc/Swagger UI possono usarlo come fonte di verita' della
 * documentazione invece delle annotation {@code @Operation}/{@code @ApiResponse}
 * generate da {@code openapi-generator-maven-plugin}.
 *
 * <p><b>Motivazione</b>: il generator 7.23 ha un bug per cui aggrega tutti i
 * content-type di un'operation e li ri-applica a OGNI response. Esempio: una
 * 200 che produce solo {@code application/json} si ritrova annotata anche
 * con {@code application/problem+json}, e la doc esposta da SpringDoc
 * mostrerebbe quel media type fuorviante per il caso di successo.
 *
 * <p>Tenendo {@code springdoc.api-docs.enabled=false} si disattiva lo
 * scanning delle annotation e Swagger UI legge esclusivamente da questo
 * endpoint il YAML committato dal team, che e' la spec autorevole della V2.
 */
@RestController
public class OpenApiSpecController {

    private static final MediaType APPLICATION_YAML = MediaType.valueOf("application/yaml");

    @GetMapping("/openapi/openapi.yaml")
    public ResponseEntity<Resource> spec() throws IOException {
        ClassPathResource resource = new ClassPathResource("openapi/openapi.yaml");
        return ResponseEntity.ok()
                .contentType(APPLICATION_YAML)
                .contentLength(resource.contentLength())
                .body(resource);
    }
}
