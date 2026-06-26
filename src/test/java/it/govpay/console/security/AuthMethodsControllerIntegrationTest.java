package it.govpay.console.security;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica end-to-end di {@code GET /auth/methods}:
 * endpoint pubblico, contenuto derivato dalle property abilitate, e header
 * di caching corretto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMethodsControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void getMethodsIsPublicAndReturnsEnabledMethods() throws Exception {
        // In profilo test: govpay.auth.basic.enabled=true + form.enabled=true.
        // Nessun'altra property auth attivata → solo BASIC + FORM esposti.
        mvc.perform(get("/auth/methods"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string("Cache-Control", is("max-age=300, public")))
                .andExpect(jsonPath("$.metodi.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.metodi[*].codice", hasItems("BASIC", "FORM")));
    }

    @Test
    void formMethodCarriesLoginPath() throws Exception {
        mvc.perform(get("/auth/methods"))
                .andExpect(status().isOk())
                // L'unico metodo con urlInizio valorizzato dal resolver e' FORM
                // (loginPath dal GovpayAuthProperties.Form). I client lo usano
                // per costruire la chiamata POST di login.
                .andExpect(jsonPath("$.metodi[?(@.codice == 'FORM')].urlInizio",
                        hasItems("/auth/login")));
    }

    @Test
    void getMethodsDoesNotRequireAuthentication() throws Exception {
        // Niente Authorization, niente session: la chain deve permit-all
        // grazie a govpay.auth.public-chain.permit-all-paths[/auth/methods].
        mvc.perform(get("/auth/methods"))
                .andExpect(status().isOk());
    }
}
