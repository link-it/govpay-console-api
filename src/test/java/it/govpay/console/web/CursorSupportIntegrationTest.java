package it.govpay.console.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * {@code ?cursor=} sulle risorse a paginazione per pagina. Prima veniva
 * ignorato: 200 senza {@code nextCursor}, e un client convinto di paginare a
 * cursore rileggeva la prima pagina all'infinito.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CursorSupportIntegrationTest {

    private static final String PRINCIPAL = "op-cursor";
    private static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private AclRepository aclRepository;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore operatore = new Operatore();
        operatore.setNome("Operatore Cursor");
        operatore.setIdUtenza(utenza.getId());
        operatoreRepository.save(operatore);

        for (String servizio : new String[] { "Anagrafica Ruoli", "Anagrafica creditore", "Pagamenti e Pendenze" }) {
            Acl acl = new Acl();
            acl.setIdUtenza(utenza.getId());
            acl.setServizio(servizio);
            acl.setDiritti("RW");
            aclRepository.save(acl);
        }
    }

    @Test
    void cursorSuRisorsaSenzaKeysetRitorna400() throws Exception {
        mvc.perform(get("/ruoli").param("cursor", "abc").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("cursor")))
                .andExpect(jsonPath("$.detail", containsString("page")));
    }

    @Test
    void cursorVuotoRitornaComunque400() throws Exception {
        // Il parametro presente e vuoto e' l'avvio della modalita' cursor dove
        // esiste: dove non esiste va segnalato allo stesso modo.
        mvc.perform(get("/intermediari").param("cursor", "").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void senzaCursorLaRisorsaRispondeNormalmente() throws Exception {
        mvc.perform(get("/ruoli").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void cursorSuRisorsaConKeysetRestaGestitoDallaRisorsa() throws Exception {
        // Le ricevute il cursore lo dichiarano: l'interceptor non interviene, e
        // il 400 che segue e' quello del codec sul cursore malformato.
        mvc.perform(get("/ricevute").param("cursor", "non-un-cursore").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("ursor")));
    }
}
