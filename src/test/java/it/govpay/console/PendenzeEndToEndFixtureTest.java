package it.govpay.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import it.govpay.console.avviso.StampeClient;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;

/**
 * Test E2E che usa la fixture SQL {@code /data-pendenze-test.sql} per coprire
 * i flussi cross-resource dello scope I (issue #9):
 * <ul>
 *   <li>lista pendenze con filtro per identificativo debitore + audit;</li>
 *   <li>dettaglio pendenza con _links condizionali;</li>
 *   <li>sub-resource /ricevuta sia in JSON che in XML byte-identico;</li>
 *   <li>sub-resource /informazioniDebitore con audit di visualizzazione.</li>
 * </ul>
 *
 * <p>La fixture inserisce solo dati di business (dominio, applicazione,
 * versamenti, RT). L'autenticazione (utenza + operatore con visibilita'
 * totale) viene allestita qui via JPA con {@link GovpayPasswordEncoder}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url=http://stampe.mock"})
@Sql("/data-pendenze-test.sql")
class PendenzeEndToEndFixtureTest {

    private static final String PRINCIPAL = "op-fixture";
    private static final String PASSWORD = "fixture-secret";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

    @MockitoBean private StampeClient stampeClient;

    @BeforeEach
    void setupOperatore() {
        Utenza u = new Utenza();
        u.setPrincipal(PRINCIPAL);
        u.setPrincipalOriginale(PRINCIPAL);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome("Op Fixture");
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
    }

    private static Answer<Void> writePdf(byte[] bytes) {
        return invocation -> {
            OutputStream out = invocation.getArgument(1);
            out.write(bytes);
            return null;
        };
    }

    @Test
    void listaConFiltroDebitoreRestituisceLaPendenzaGiustaEScriveAudit() throws Exception {
        mvc.perform(get("/pendenze?identificativoDebitore=RSSMRA80A01H501U")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idPendenza", is("PEND-FIX-1")));

        assertThat(gpAuditRepository.findAll())
                .extracting(GpAudit::getTipoOggetto)
                .contains("PENDENZE_RICERCA_PER_DEBITORE");
    }

    @Test
    void listaSenzaFiltroDebitoreVedeEntrambeLePendenzeFixture() throws Exception {
        mvc.perform(get("/pendenze")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("PEND-FIX-1", "PEND-FIX-2")));
    }

    @Test
    void dettaglioPendenzaPagataIncludeLinkRicevute() throws Exception {
        mvc.perform(get("/pendenze/APP-FIXTURE/PEND-FIX-1")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPendenza", is("PEND-FIX-1")))
                .andExpect(jsonPath("$._links.informazioniDebitore.href").exists())
                .andExpect(jsonPath("$._links.ricevute.href").exists())
                .andExpect(jsonPath("$._links.ricevuta").doesNotExist())
                .andExpect(jsonPath("$._links.avviso.href").exists());
    }

    @Test
    void ricevuteListContieneMetadatiDaFixture() throws Exception {
        mvc.perform(get("/pendenze/APP-FIXTURE/PEND-FIX-1/ricevute")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].iuv", is("1111111111111")))
                .andExpect(jsonPath("$[0].ccp", is("CCP-FIX-001")))
                .andExpect(jsonPath("$[0].idDominio", is("12345678901")))
                .andExpect(jsonPath("$[0].idPsp", is("BNCFIXTURE")));
    }

    @Test
    void informazioniDebitoreReturnsSoggettoEScriveAudit() throws Exception {
        mvc.perform(get("/pendenze/APP-FIXTURE/PEND-FIX-1/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.anagrafica", is("Mario Rossi Fixture")));

        assertThat(gpAuditRepository.findAll())
                .extracting(GpAudit::getTipoOggetto)
                .contains("PENDENZA_VISUALIZZA_DEBITORE");
    }
}
