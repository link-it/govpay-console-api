package it.govpay.console.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifica che il YAML sorgente di OpenAPI sia esposto come fonte di verita'
 * della documentazione, senza autenticazione, e che il contenuto del file
 * committato dal team venga restituito as-is (niente trasformazione SpringDoc).
 *
 * <p>Difende dal regressione "doc esposta fuorviante" causata dal bug del
 * generator 7.23 che ripeteva {@code application/problem+json} sulle response
 * 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void openapiYamlIsExposedPubliclyWithCorrectMediaType() throws Exception {
        mvc.perform(get("/openapi/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/yaml")))
                // Verifica che sia proprio il YAML committato (header info.title)
                .andExpect(content().string(containsString("title: GovPay Console API")));
    }

    /**
     * Parsa strutturalmente il YAML e verifica che NESSUNA response 2xx
     * dichiari il media type {@code application/problem+json}. Quel media
     * type e' riservato agli errori 4xx/5xx (RFC 7807). Difende dal
     * regressione "qualcuno aggiunge problem+json a una 200" — la versione
     * precedente del test guardava solo la presenza di un {@code $ref} al
     * {@code Profilo}, lasciando passare YAML con problem+json mischiato
     * fra i content type del success.
     */
    @Test
    void openapiYamlNeverDeclaresProblemJsonOn2xxResponses() throws Exception {
        Map<String, Object> doc = loadOpenapiYaml();

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : asMap(doc.get("paths")).entrySet()) {
            String path = pathEntry.getKey();
            for (Map.Entry<String, Object> opEntry : asMap(pathEntry.getValue()).entrySet()) {
                String method = opEntry.getKey();
                if (!HTTP_METHODS.contains(method.toLowerCase())) {
                    continue;
                }
                Map<String, Object> responses = asMap(asMap(opEntry.getValue()).get("responses"));
                for (Map.Entry<String, Object> respEntry : responses.entrySet()) {
                    String statusCode = respEntry.getKey();
                    if (!isHttpStatus2xx(statusCode)) {
                        continue;
                    }
                    Map<String, Object> content = asMap(asMap(respEntry.getValue()).get("content"));
                    if (content.containsKey("application/problem+json")) {
                        violations.add(method.toUpperCase() + " " + path + " → " + statusCode);
                    }
                }
            }
        }

        assertThat(violations)
                .as("response 2xx NON devono dichiarare application/problem+json"
                        + " (riservato agli errori 4xx/5xx, RFC 7807)")
                .isEmpty();
    }

    /**
     * Simmetrico: ogni response 4xx/5xx con body dichiarato deve usare
     * {@code application/problem+json}. Sentinella anti-divergenza dal
     * pattern problem+json scelto per il progetto.
     */
    @Test
    void openapiYamlAlwaysDeclaresProblemJsonOn4xx5xxErrorResponsesWithContent() throws Exception {
        Map<String, Object> doc = loadOpenapiYaml();

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : asMap(doc.get("paths")).entrySet()) {
            String path = pathEntry.getKey();
            for (Map.Entry<String, Object> opEntry : asMap(pathEntry.getValue()).entrySet()) {
                String method = opEntry.getKey();
                if (!HTTP_METHODS.contains(method.toLowerCase())) {
                    continue;
                }
                Map<String, Object> responses = asMap(asMap(opEntry.getValue()).get("responses"));
                for (Map.Entry<String, Object> respEntry : responses.entrySet()) {
                    String statusCode = respEntry.getKey();
                    if (!isHttpStatus4xx5xx(statusCode)) {
                        continue;
                    }
                    Map<String, Object> content = asMap(asMap(respEntry.getValue()).get("content"));
                    if (content.isEmpty()) {
                        // Body assente (es. 204) — legittimo
                        continue;
                    }
                    if (!content.containsKey("application/problem+json")) {
                        violations.add(method.toUpperCase() + " " + path + " → " + statusCode
                                + " (media types: " + content.keySet() + ")");
                    }
                }
            }
        }

        assertThat(violations)
                .as("response 4xx/5xx con body devono usare application/problem+json (RFC 7807)")
                .isEmpty();
    }

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "options", "head");

    private static boolean isHttpStatus2xx(String code) {
        return code != null && code.length() == 3 && code.charAt(0) == '2';
    }

    private static boolean isHttpStatus4xx5xx(String code) {
        return code != null && code.length() == 3 && (code.charAt(0) == '4' || code.charAt(0) == '5');
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> loadOpenapiYaml() throws Exception {
        try (var stream = new ClassPathResource("openapi/openapi.yaml").getInputStream()) {
            return new Yaml().load(stream);
        }
    }

    /**
     * Verifica che {@code /swagger-ui.html} sia raggiungibile senza auth e
     * rediriga a {@code /swagger-ui/index.html} (la HTML page del webjar).
     * Difende dal regressione "Swagger UI spenta perche' api-docs
     * disabilitato" (SpringDoc 3.x: {@code api-docs.enabled=false} spegne
     * anche l'infrastructure UI; va tenuto {@code true} con
     * {@code paths-to-exclude=/**} per non scannerizzare i controller).
     */
    @Test
    void swaggerUiHtmlRedirectsToWebjarIndex() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    /**
     * SpringDoc 3.x espone {@code /v3/api-docs/swagger-config} come endpoint
     * di bootstrap della UI: e' il primo GET che la index.html fa per
     * scoprire l'URL della spec da caricare. Deve essere pubblico e
     * deve contenere il NOSTRO YAML come {@code url}/{@code configUrl},
     * non il {@code /v3/api-docs} generato da SpringDoc (che e' vuoto per
     * via di {@code paths-to-exclude}).
     */
    @Test
    void swaggerConfigPointsToCustomYaml() throws Exception {
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/openapi/openapi.yaml")));
    }

    // NB: /swagger-ui/index.html (la HTML page vera) e' servita come static
    // resource da un webjar via SpringDoc resource handler. MockMvc non emula
    // affidabilmente quel path nel test setup, quindi la verifica "pagina HTML
    // ricevuta" va fatta in dev. I due test sopra sono sufficienti per
    // certificare che l'infrastructure SpringDoc e' attiva e configurata sul
    // YAML giusto.
}
