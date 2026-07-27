package it.govpay.console.pagopaiban;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.IbanCache;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.IbanCacheRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IbanControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
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
    private IbanCacheRepository ibanCacheRepository;

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

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        newIban("77777777777", "IT60X0542811101000000123456", true);
        newIban("77777777777", "IT60X0542811101000000789012", false);
        newIban("88888888888", "IT60X0542811101000000999999", true);
    }

    private void newIban(String codDominio, String iban, boolean attivo) {
        IbanCache e = new IbanCache();
        e.setCodDominio(codDominio);
        e.setIban(iban);
        e.setAttivo(attivo);
        e.setDataUltimaVerifica(OffsetDateTime.now());
        ibanCacheRepository.save(e);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pagopa/iban").param("idDominio", "77777777777"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void missingIdDominio_returns400() throws Exception {
        mvc.perform(get("/pagopa/iban").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listReturnsOnlyIbanOfRequestedDominio() throws Exception {
        mvc.perform(get("/pagopa/iban").param("idDominio", "77777777777").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].iban", contains(
                        "IT60X0542811101000000123456", "IT60X0542811101000000789012")));
    }

    @Test
    void listExposesAttivoAndTimestamp() throws Exception {
        mvc.perform(get("/pagopa/iban").param("idDominio", "77777777777").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attivo").value(true))
                .andExpect(jsonPath("$[0].dataUltimaVerificaPagopa").exists())
                .andExpect(jsonPath("$[1].attivo").value(false));
    }

    @Test
    void unknownDominio_returnsEmptyArray() throws Exception {
        mvc.perform(get("/pagopa/iban").param("idDominio", "00000000000").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
