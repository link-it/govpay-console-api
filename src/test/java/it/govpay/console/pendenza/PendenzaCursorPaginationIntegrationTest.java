package it.govpay.console.pendenza;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PendenzaCursorPaginationIntegrationTest {

    private static final String PRINCIPAL = "operatore-g";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-G";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;

    @BeforeEach
    void setup() {
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
        op.setNome("Op G");
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        Dominio dom = new Dominio();
        dom.setCodDominio("77777777777");
        dom.setRagioneSociale("Comune G");
        dom.setAuxDigit(0);
        dominioRepository.save(dom);

        Applicazione app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);

        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dom);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);

        // 7 pendenze con dataOraUltimoAggiornamento decrescente: PEND-1 piu' recente
        // (offset 0 ore), PEND-7 piu' vecchia (offset 6 ore). Cursor mode → DESC,
        // quindi i risultati arriveranno PEND-1, PEND-2, ..., PEND-7.
        for (int i = 1; i <= 7; i++) {
            newPendenza("PEND-" + i, dom, app, tv, tvd, i - 1);
        }
    }

    private void newPendenza(String idPendenza, Dominio dom, Applicazione app,
                             TipoVersamento tv, TipoVersamentoDominio tvd, int hoursAgo) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("NON_ESEGUITO");
        v.setDataCreazione(OffsetDateTime.now().minusHours(hoursAgo));
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now().minusHours(hoursAgo));
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setDominio(dom);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        versamentoRepository.save(v);
    }

    @Test
    void primaPaginaCursorMode_returnsNextCursor() throws Exception {
        mvc.perform(get("/pendenze?cursor=&limit=3")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].idPendenza", is("PEND-1")))
                .andExpect(jsonPath("$.results[1].idPendenza", is("PEND-2")))
                .andExpect(jsonPath("$.results[2].idPendenza", is("PEND-3")))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andExpect(jsonPath("$.pagination").doesNotExist());
    }

    @Test
    void seguenteCursore_returnsNextSlice() throws Exception {
        // prima pagina
        MvcResult first = mvc.perform(get("/pendenze?cursor=&limit=3")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String body = first.getResponse().getContentAsString();
        String cursor = extractStringField(body, "nextCursor");

        mvc.perform(get("/pendenze?cursor=" + cursor + "&limit=3")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].idPendenza", is("PEND-4")))
                .andExpect(jsonPath("$.results[2].idPendenza", is("PEND-6")))
                .andExpect(jsonPath("$.nextCursor", notNullValue()));
    }

    @Test
    void ultimaPaginaSenzaNextCursor() throws Exception {
        // skip prime 6: cursor punta a PEND-6, prossima pagina = solo PEND-7
        MvcResult r1 = mvc.perform(get("/pendenze?cursor=&limit=6")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String cursor = extractStringField(r1.getResponse().getContentAsString(), "nextCursor");

        mvc.perform(get("/pendenze?cursor=" + cursor + "&limit=6")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idPendenza", is("PEND-7")))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void cursorPlusPageReturns400WithParlanteMessage() throws Exception {
        mvc.perform(get("/pendenze?cursor=&page=2")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("'page' e 'cursor' mutuamente esclusivi")));
    }

    @Test
    void cursorPlusSortReturns400WithParlanteMessage() throws Exception {
        mvc.perform(get("/pendenze?cursor=&sort=-importo")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail",
                        containsString("In modalita' cursor")))
                .andExpect(jsonPath("$.detail",
                        containsString("ordinamento e' fisso")));
    }

    @Test
    void cursorPlusTotalReturns400WithParlanteMessage() throws Exception {
        mvc.perform(get("/pendenze?cursor=&total=true")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("conteggio totale non e' disponibile")));
    }

    @Test
    void cursorMalformatoReturns400() throws Exception {
        mvc.perform(get("/pendenze?cursor=!!!not-base64!!!")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", containsString("Cursor malformato")));
    }

    private static String extractStringField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Campo '" + field + "' assente nel body: " + json);
        }
        return m.group(1);
    }
}
