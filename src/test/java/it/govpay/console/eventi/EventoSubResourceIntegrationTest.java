package it.govpay.console.eventi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.Header;

/**
 * Integration test dei sub-resource {@code GET /eventi/{id}/richiesta} e
 * {@code .../risposta} (issue #51, step 3): redazione header, {@code ?unmask=true},
 * audit GDPR. {@link EventoGdeClient} mockato (HTTP gia' verificato in
 * {@link EventoGdeClientTest}); {@code gp_audit} e' l'H2 reale di test, cosi'
 * si verifica che le righe di audit vengano davvero scritte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventoSubResourceIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

    @MockitoBean
    private EventoGdeClient eventoGdeClient;

    @BeforeEach
    void setup() {
        Dominio d = new Dominio();
        d.setCodDominio(DOM_A);
        d.setRagioneSociale("Dominio A");
        d.setAuxDigit(0);
        dominioRepository.save(d);
    }

    @Test
    void richiesta_default_headerSensibiliMascherati_eAuditScritto() throws Exception {
        String p = utenteDominiStar("u-req");
        when(eventoGdeClient.getEventoById(1L)).thenReturn(new Evento()
                .id(1L)
                .idDominio(DOM_A)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(
                                new Header().nome("Authorization").valore("Basic dGVzdDp0ZXN0"),
                                new Header().nome("Content-Type").valore("application/xml")))
                        .payload("aGVsbG8=")));

        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/eventi/1/richiesta").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload", is("hello")))
                .andExpect(jsonPath("$.headers[?(@.nome=='Authorization')].valore", org.hamcrest.Matchers.contains("***REDACTED***")))
                .andExpect(jsonPath("$.headers[?(@.nome=='Authorization')].redatto", org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.headers[?(@.nome=='Content-Type')].valore", org.hamcrest.Matchers.contains("application/xml")));

        assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 1);
        boolean trovato = gpAuditRepository.findAll().stream().anyMatch(a ->
                EventoSubResourceService.AZIONE_AUDIT_RICHIESTA.equals(a.getTipoOggetto()) && a.getIdOggetto() == 1L);
        assertThat(trovato).isTrue();
    }

    @Test
    void richiesta_unmask_mostraHeaderInChiaro_eScriveDueRecordAudit() throws Exception {
        String p = utenteDominiStar("u-unmask");
        when(eventoGdeClient.getEventoById(2L)).thenReturn(new Evento()
                .id(2L)
                .idDominio(DOM_A)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().nome("Authorization").valore("Basic dGVzdDp0ZXN0")))));

        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/eventi/2/richiesta?unmask=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers[0].valore", is("Basic dGVzdDp0ZXN0")))
                .andExpect(jsonPath("$.headers[0].redatto", is(false)));

        assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 2);
        List<String> azioni = gpAuditRepository.findAll().stream()
                .filter(a -> a.getIdOggetto() == 2L)
                .map(GpAudit::getTipoOggetto).toList();
        assertThat(azioni).contains(EventoSubResourceService.AZIONE_AUDIT_RICHIESTA,
                EventoSubResourceService.AZIONE_AUDIT_CREDENZIALI);
    }

    @Test
    void risposta_default_ok() throws Exception {
        String p = utenteDominiStar("u-resp");
        when(eventoGdeClient.getEventoById(3L)).thenReturn(new Evento()
                .id(3L)
                .idDominio(DOM_A)
                .parametriRisposta(new DettaglioRisposta()
                        .headers(List.of(new Header().nome("Set-Cookie").valore("JSESSIONID=abc")))
                        .payload("d29ybGQ=")));

        mvc.perform(get("/eventi/3/risposta").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload", is("world")))
                .andExpect(jsonPath("$.headers[0].redatto", is(true)));
    }

    @Test
    void richiesta_nonRegistrata_ritorna404_senzaAudit() throws Exception {
        String p = utenteDominiStar("u-404nr");
        when(eventoGdeClient.getEventoById(4L)).thenReturn(new Evento().id(4L).idDominio(DOM_A));

        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/eventi/4/richiesta").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());

        assertThat(gpAuditRepository.count()).isEqualTo(auditPrima);
    }

    @Test
    void richiesta_filtroNonSupportato_ritorna400() throws Exception {
        String p = utenteDominiStar("u-400");
        mvc.perform(get("/eventi/1/richiesta?formato=json").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void richiesta_gdeNonRaggiungibile_ritorna502() throws Exception {
        String p = utenteDominiStar("u-502");
        when(eventoGdeClient.getEventoById(5L)).thenThrow(new GdeNonRaggiungibileException("giu'", new RuntimeException()));

        mvc.perform(get("/eventi/5/richiesta").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadGateway());
    }

    private String utenteDominiStar(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
        return principal;
    }
}
