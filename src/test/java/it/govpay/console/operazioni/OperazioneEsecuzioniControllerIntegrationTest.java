package it.govpay.console.operazioni;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.common.batch.dto.ExecutionSummaryInfo;
import it.govpay.common.batch.dto.ExecutionsPage;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;

/**
 * Il client verso il microservizio batch (govpay-common {@code AbstractBatchController})
 * e' mockato a livello di bean: paginazione/ordinamento/filtri sono
 * responsabilita' di govpay-common (gia' verificati in
 * {@code AbstractBatchControllerExecutionsTest}); qui si verifica solo il
 * cablaggio controller/mappatura/sicurezza.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperazioneEsecuzioniControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String IBAN_URL = "http://iban-batch:8080/api/batch";

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

    @MockitoBean
    private OperazioneBatchClient client;

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
    }

    private void grantScrittura() {
        Utenza utenza = utenzaRepository.findByPrincipal(PRINCIPAL).orElseThrow();
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio("Configurazione e manutenzione");
        acl.setDiritti("RW");
        aclRepository.save(acl);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownOperazioneReturns404() throws Exception {
        mvc.perform(get("/operazioni/BOGUS/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void listaVuotaPerOperazioneLocale() throws Exception {
        mvc.perform(get("/operazioni/RESET_CACHE/esecuzioni").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void list_passaAttraversoIParametriEMappaLaRisposta() throws Exception {
        when(client.listExecutions(eq(IBAN_URL), eq("FAILED,UNKNOWN"), isNull(), isNull(), eq(1), eq(2), eq(true)))
                .thenReturn(ExecutionsPage.builder()
                        .results(java.util.List.of(ExecutionSummaryInfo.builder()
                                .executionId(7L).status("FAILED").startTime(java.time.LocalDateTime.now()).build()))
                        .page(1).limit(2).hasNextPage(false).totalResults(1L).totalPages(1)
                        .build());

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni")
                        .param("stato", "FALLITA").param("page", "1").param("limit", "2").param("total", "true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.results[0].idEsecuzione").value("7"))
                .andExpect(jsonPath("$.results[0].stato").value("FALLITA"))
                .andExpect(jsonPath("$.pagination.totalResults").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1));
    }

    @Test
    void dettaglioEsecuzioneEsistente() throws Exception {
        when(client.getExecution(IBAN_URL, 42L)).thenReturn(LastExecutionInfo.builder()
                .executionId(42L).status("COMPLETED").startTime(java.time.LocalDateTime.now()).build());

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/42").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.idEsecuzione").value("42"))
                .andExpect(jsonPath("$.idOperazione").value("IBAN_CHECK"))
                .andExpect(jsonPath("$.stato").value("COMPLETATA"))
                .andExpect(jsonPath("$.forzata").value((Object) null));
    }

    @Test
    void dettaglioIdInesistenteReturns404() throws Exception {
        when(client.getExecution(IBAN_URL, 999999L)).thenThrow(new NotFoundException("non trovata"));

        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/999999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void dettaglioPerOperazioneLocaleReturns404() throws Exception {
        mvc.perform(get("/operazioni/RESET_CACHE/esecuzioni/1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglioIdNonNumericoReturns400() throws Exception {
        mvc.perform(get("/operazioni/IBAN_CHECK/esecuzioni/not-a-number").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void annullaEsecuzione_delegaAlClientReturns202() throws Exception {
        grantScrittura();

        mvc.perform(delete("/operazioni/IBAN_CHECK/esecuzioni/42").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isAccepted());

        verify(client).stopExecution(IBAN_URL, 42L);
    }

    @Test
    void annullaEsecuzioneNonInCorsoReturns409() throws Exception {
        grantScrittura();
        org.mockito.Mockito.doThrow(new ConflictException("non in corso")).when(client).stopExecution(IBAN_URL, 42L);

        mvc.perform(delete("/operazioni/IBAN_CHECK/esecuzioni/42").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void annullaEsecuzioneIdInesistenteReturns404() throws Exception {
        grantScrittura();
        org.mockito.Mockito.doThrow(new NotFoundException("non trovata")).when(client).stopExecution(IBAN_URL, 999999L);

        mvc.perform(delete("/operazioni/IBAN_CHECK/esecuzioni/999999").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void annullaEsecuzioneOperazioneSconosciutaReturns404() throws Exception {
        grantScrittura();
        mvc.perform(delete("/operazioni/BOGUS/esecuzioni/1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void annullaEsecuzionePerOperazioneLocaleReturns404() throws Exception {
        grantScrittura();
        mvc.perform(delete("/operazioni/RESET_CACHE/esecuzioni/1").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void annullaEsecuzioneSenzaDirittoScritturaReturns403() throws Exception {
        mvc.perform(delete("/operazioni/IBAN_CHECK/esecuzioni/42").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void annullaEsecuzioneSenzaAutenticazioneReturns401() throws Exception {
        mvc.perform(delete("/operazioni/IBAN_CHECK/esecuzioni/1"))
                .andExpect(status().isUnauthorized());
    }
}
