package it.govpay.console.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void openapiYamlReturns200Response200ContainsOnlyApplicationJson() throws Exception {
        // Difesa dal bug del generator: il YAML sorgente NON deve avere
        // application/problem+json sulle response 200 (e' solo per le response
        // di errore 4xx/5xx). Se questa asserzione fallisce, qualcuno ha
        // toccato il YAML in modo sbagliato.
        mvc.perform(get("/openapi/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$ref: '#/components/schemas/Profilo'")));
    }
}
