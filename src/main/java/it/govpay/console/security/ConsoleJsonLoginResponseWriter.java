package it.govpay.console.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import it.govpay.common.auth.spi.JsonLoginResponseWriter;
import it.govpay.console.model.AutenticazioneEnum;
import it.govpay.console.model.Profilo;
import it.govpay.console.profilo.ProfiloService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Implementazione di {@link JsonLoginResponseWriter} che, sul successo
 * di {@code POST /auth/login}, scrive il {@link Profilo} dell'utenza
 * autenticata come body della response — il frontend non deve fare un
 * secondo round-trip a {@code GET /profilo}.
 *
 * <p>{@code autenticazione} viene fissata a {@code FORM}: la SPI e'
 * invocata solo dal flusso JSON login, niente ambiguita' di metodo.
 */
@Component
public class ConsoleJsonLoginResponseWriter implements JsonLoginResponseWriter {

    private final ProfiloService profiloService;
    private final ObjectMapper objectMapper;

    public ConsoleJsonLoginResponseWriter(ProfiloService profiloService, ObjectMapper objectMapper) {
        this.profiloService = profiloService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeSuccessBody(HttpServletResponse response, Authentication authentication) throws IOException {
        Profilo profilo = profiloService.build();
        profilo.setAutenticazione(AutenticazioneEnum.FORM);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), profilo);
    }
}
