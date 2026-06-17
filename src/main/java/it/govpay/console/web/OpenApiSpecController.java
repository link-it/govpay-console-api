package it.govpay.console.web;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espone il file {@code openapi.yaml} sorgente come endpoint statico.
 * Swagger UI lo carica come spec autorevole tramite
 * {@code springdoc.swagger-ui.url}, evitando la spec auto-generata da
 * {@code @Operation}/{@code @ApiResponse} (che {@code openapi-generator}
 * popola in modo inaccurato sui media type per response).
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
