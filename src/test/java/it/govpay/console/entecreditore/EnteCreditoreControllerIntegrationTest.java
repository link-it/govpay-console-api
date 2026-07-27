package it.govpay.console.entecreditore;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.EnteCreditoreCache;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.EnteCreditoreCacheRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnteCreditoreControllerIntegrationTest {

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
    private EnteCreditoreCacheRepository enteCreditoreCacheRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

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

        newEnte("77777777777", "Comune di Alfa", "77777777777001", "3", "01", "AAAAAA");
        newEnte("88888888888", "Comune di Beta", "88888888888001", "3", "02", "BBBBBB");
        newEnte("99999999999", "Provincia di Gamma", null, "0", null, null);
    }

    private void newEnte(String codFiscale, String denominazione, String stationId,
                         String auxDigit, String segregationCode, String cbillCode) {
        EnteCreditoreCache e = new EnteCreditoreCache();
        e.setCodFiscale(codFiscale);
        e.setDenominazione(denominazione);
        e.setStationId(stationId);
        e.setAuxDigit(auxDigit);
        e.setSegregationCode(segregationCode);
        e.setCbillCode(cbillCode);
        e.setDataUltimoAggiornamento(OffsetDateTime.now());
        enteCreditoreCacheRepository.save(e);
    }

    // --- List ---

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listDefaultIsSliceWithoutTotals() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(false)))
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    @Test
    void listTotalTrueIncludesCount() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("total", "true").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", is(3)))
                .andExpect(jsonPath("$.pagination.totalPages", is(1)));
    }

    @Test
    void listSliceSignalsHasNextPage() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("limit", "2").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)));
    }

    @Test
    void searchMatchesTaxCodePartial() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("search", "7777777").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].taxCode", is("77777777777")));
    }

    @Test
    void searchMatchesCompanyNamePartialCaseInsensitive() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("search", "gamma").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].taxCode", is("99999999999")));
    }

    @Test
    void defaultSortByCompanyNameAsc() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].companyName",
                        contains("Comune di Alfa", "Comune di Beta", "Provincia di Gamma")));
    }

    @Test
    void customSortDesc() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("sort", "-taxCode").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].taxCode",
                        contains("99999999999", "88888888888", "77777777777")));
    }

    @Test
    void unknownSortFieldReturns400() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori").param("sort", "-bogus").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("bogus")));
    }

    // --- Get detail ---

    @Test
    void getExistingReturnsDetail() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori/77777777777").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxCode", is("77777777777")))
                .andExpect(jsonPath("$.companyName", is("Comune di Alfa")))
                .andExpect(jsonPath("$.stationId", is("77777777777001")))
                .andExpect(jsonPath("$.auxDigit", is("3")))
                .andExpect(jsonPath("$.segregationCode", is("01")))
                .andExpect(jsonPath("$.cbill", is("AAAAAA")))
                .andExpect(jsonPath("$.dataUltimoAggiornamento").exists());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/pagopa/enti-creditori/00000000000").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("00000000000")));
    }

    @Test
    void getWritesAudit() throws Exception {
        long before = countAudit("ENTE_CREDITORE_VISUALIZZA");
        mvc.perform(get("/pagopa/enti-creditori/77777777777").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());
        Assertions.assertThat(countAudit("ENTE_CREDITORE_VISUALIZZA")).isEqualTo(before + 1);
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
