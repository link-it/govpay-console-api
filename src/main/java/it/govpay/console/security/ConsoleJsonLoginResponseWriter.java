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
 * Implementazione console-api della SPI {@link JsonLoginResponseWriter}.
 * Sul successo di {@code POST /auth/login} ritorna sulla stessa response
 * il {@link Profilo} dell'utenza appena autenticata, evitando un secondo
 * round-trip {@code GET /profilo} dal frontend (scope C issue #10).
 *
 * <p>Il metodo di autenticazione e' sempre {@code FORM}: questo writer
 * e' invocato solo dal {@code JsonUsernamePasswordAuthenticationFilter}
 * (la libreria non riusa questo SPI per BASIC/SSL/...).
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
