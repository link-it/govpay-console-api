package it.govpay.console.ricevuta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.avviso.StampeClient;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.ConsolePagamentoRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.Rpt;
import it.govpay.pendenze.model.StatoPagamentoRicevuta;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.TipoPagamento;
import it.govpay.stampe.client.model.Receipt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Ricevute senza pendenza ({@code rpt.id_versamento} nullo): ricerca, filtri, visibilita',
 * dettaglio e sub-risorse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicevuteSenzaPendenzaIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM = "44444444444";
    private static final String ALTRO_DOM = "55555555555";
    private static final String ORFANA = "/ricevute/" + DOM + "/IUV-ORFANA/CCP-ORFANA";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private AclRepository aclRepository;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired private ConsoleRptRepository rptRepository;
    @Autowired private ConsolePagamentoRepository pagamentoRepository;
    @PersistenceContext private EntityManager em;

    @MockitoBean private StampeClient stampeClient;

    private Dominio dominio;
    private Dominio altroDominio;
    private UnitaOperativa uoX;
    private UnitaOperativa uoEc;
    private TipoVersamento tari;
    private Applicazione app;
    private Rpt orfana;

    @BeforeEach
    void setup() throws IOException {
        dominio = dominio(DOM);
        altroDominio = dominio(ALTRO_DOM);
        uoX = uo("UO-X");
        uoEc = uo("EC");

        app = new Applicazione();
        app.setCodApplicazione("APP-ORF");
        applicazioneRepository.save(app);

        tari = new TipoVersamento();
        tari.setCodTipoVersamento("TARI-ORF");
        tari.setDescrizione("TARI");
        tipoVersamentoRepository.save(tari);
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tari);
        tipoVersamentoDominioRepository.save(tvd);

        Pendenza pendenza = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(), tari.getId(),
                tvd.getId(), uoX.getId(), "CON-PENDENZA", null, "RSSMRA80A01H501U", 100.0,
                StatoPendenza.ESEGUITO, 0));
        em.flush();
        ricevuta("CON-PENDENZA", pendenza.getId(), null);
        orfana = ricevuta("ORFANA", null, app.getId());
        ricevuta("SENZA-APP", null, null);
    }

    private Dominio dominio(String codice) {
        Dominio d = new Dominio();
        d.setCodDominio(codice);
        d.setRagioneSociale("Dominio " + codice);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private UnitaOperativa uo(String codice) {
        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo(codice);
        uo.setUoDenominazione(codice);
        uo.setDominio(dominio);
        return unitaOperativaRepository.save(uo);
    }

    private Rpt ricevuta(String nome, Long idVersamento, Long idApplicazione) throws IOException {
        OffsetDateTime data = OffsetDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneOffset.UTC);
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("CCP-" + nome);
        r.setDataAggiornamentoStato(data);
        r.setIuv("IUV-" + nome);
        r.setIur("CCP-" + nome);
        r.setCodDominio(DOM);
        r.setXmlRt(risorsa("rt-v2-ok.xml"));
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(10.0);
        r.setDataMsgRichiesta(data.minusHours(1));
        r.setDataMsgRicevuta(data);
        r.setIdVersamento(idVersamento);
        r.setIdApplicazione(idApplicazione);
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        return rptRepository.save(r);
    }

    private static byte[] risorsa(String nome) throws IOException {
        try (InputStream in = RicevuteSenzaPendenzaIntegrationTest.class.getClassLoader().getResourceAsStream(nome)) {
            return in.readAllBytes();
        }
    }

    /** Operatore con un solo vincolo di dominio: intero, oppure limitato alla unita' operativa. */
    private String utente(String principal, Dominio visibile, UnitaOperativa uo, TipoVersamento soloTipo) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(visibile == null);
        u.setAutorizzazioneTipiVersStar(soloTipo == null);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);
        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
        if (visibile != null) {
            UtenzaDominio link = new UtenzaDominio();
            link.setIdUtenza(u.getId());
            link.setIdDominio(visibile.getId());
            if (uo != null) {
                link.setIdUo(uo.getId());
            }
            utenzaDominioRepository.save(link);
        }
        if (soloTipo != null) {
            UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
            utv.setIdUtenza(u.getId());
            utv.setIdTipoVersamento(soloTipo.getId());
            utenzaTipoVersamentoRepository.save(utv);
        }
        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("R");
        aclRepository.save(acl);
        return principal;
    }

    private String tuttiIDomini() {
        return utente("orf-star", null, null, null);
    }

    private ResultActions leggi(String principal, String url) throws Exception {
        return mvc.perform(get(url).with(httpBasic(principal, PASSWORD)));
    }

    private void listaContiene(String principal, String query, String... iuv) throws Exception {
        leggi(principal, "/ricevute" + query).andExpect(status().isOk())
                .andExpect(iuv.length == 0 ? jsonPath("$.results", empty())
                        : jsonPath("$.results[*].iuv", containsInAnyOrder(iuv)));
    }

    // ----- visibilita' -----------------------------------------------------------------

    @Test
    void chiHaTuttiIDominiVedeLeRicevuteSenzaPendenza() throws Exception {
        String p = tuttiIDomini();

        listaContiene(p, "", "IUV-CON-PENDENZA", "IUV-ORFANA", "IUV-SENZA-APP");
        leggi(p, ORFANA).andExpect(status().isOk());
    }

    @Test
    void chiHaIlDominioInteroLeVede() throws Exception {
        String p = utente("orf-intero", dominio, null, null);

        listaContiene(p, "", "IUV-CON-PENDENZA", "IUV-ORFANA", "IUV-SENZA-APP");
        leggi(p, ORFANA).andExpect(status().isOk());
    }

    @Test
    void laUoEcLeVede() throws Exception {
        String p = utente("orf-ec", dominio, uoEc, null);

        listaContiene(p, "", "IUV-ORFANA", "IUV-SENZA-APP");
        leggi(p, ORFANA).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "/rt", "/rpt" })
    void unaUoDiversaDaEcNonLeVede(String risorsa) throws Exception {
        String p = utente("orf-uox", dominio, uoX, null);

        listaContiene(p, "", "IUV-CON-PENDENZA");
        leggi(p, ORFANA + risorsa).andExpect(status().isNotFound());
    }

    @Test
    void unAltroDominioNonLeVede() throws Exception {
        String p = utente("orf-altro", altroDominio, null, null);

        listaContiene(p, "");
        leggi(p, ORFANA).andExpect(status().isNotFound());
    }

    @Test
    void ilLimiteSuiTipiPendenzaNonSiApplica() throws Exception {
        TipoVersamento altro = new TipoVersamento();
        altro.setCodTipoVersamento("ALTRO-ORF");
        altro.setDescrizione("Altro");
        tipoVersamentoRepository.save(altro);
        String p = utente("orf-tipo", dominio, null, altro);

        listaContiene(p, "", "IUV-ORFANA", "IUV-SENZA-APP");
        leggi(p, ORFANA).andExpect(status().isOk());
    }

    // ----- filtri ----------------------------------------------------------------------

    @Test
    void iFiltriSuiDatiDellaRptLeTrovano() throws Exception {
        String p = tuttiIDomini();
        LocalDate giorno = LocalDate.of(2026, 9, 10);

        listaContiene(p, "?iuv=IUV-ORFANA", "IUV-ORFANA");
        listaContiene(p, "?idRicevuta=CCP-ORFANA", "IUV-ORFANA");
        listaContiene(p, "?idDominio=" + DOM, "IUV-CON-PENDENZA", "IUV-ORFANA", "IUV-SENZA-APP");
        listaContiene(p, "?dataRicevutaDa=" + giorno + "&dataRicevutaA=" + giorno + "&iuv=IUV-ORFANA",
                "IUV-ORFANA");
    }

    @Test
    void ilFiltroPerApplicazioneUsaQuellaDellaRicevuta() throws Exception {
        String p = tuttiIDomini();

        listaContiene(p, "?idA2A=APP-ORF", "IUV-CON-PENDENZA", "IUV-ORFANA");
        listaContiene(p, "?idA2A=SCONOSCIUTA");
    }

    @ParameterizedTest
    @ValueSource(strings = { "idUnitaOperativa=EC", "idUnitaOperativa=UO-X", "idPendenza=ORFANA",
            "identificativoDebitore=RSSMRA80A01H501U", "anagraficaDebitore=Anagrafica", "idTipoPendenza=TARI-ORF",
            "direzione=Dir-X", "divisione=Div-Y" })
    void iFiltriSuPendenzaDebitoreEUnitaOperativaNonLeTrovano(String filtro) throws Exception {
        String p = tuttiIDomini();

        leggi(p, "/ricevute?iuv=IUV-ORFANA&" + filtro).andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void laPaginazioneACursoreLeAttraversaTutte() throws Exception {
        String p = tuttiIDomini();

        leggi(p, "/ricevute?limit=2").andExpect(status().isOk()).andExpect(jsonPath("$.results", hasSize(2)));
        leggi(p, "/ricevute?page=2&limit=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)));
    }

    /** Un {@code id_versamento} senza pendenza non e' una ricevuta senza pendenza. */
    @Test
    void unRiferimentoAUnaPendenzaInesistenteRestaEscluso() throws Exception {
        ricevuta("PENDENTE", 999_999L, app.getId());
        String p = tuttiIDomini();
        String limitato = utente("orf-intero", dominio, null, null);

        listaContiene(p, "?iuv=IUV-PENDENTE");
        listaContiene(limitato, "?iuv=IUV-PENDENTE");
        listaContiene(p, "?iuv=IUV-PENDENTE&idA2A=APP-ORF");
        leggi(p, "/ricevute/" + DOM + "/IUV-PENDENTE/CCP-PENDENTE").andExpect(status().isNotFound());
    }

    // ----- dettaglio e sub-risorse -------------------------------------------------------

    @Test
    void ilDettaglioNonRiportaLaPendenzaEMostraLeVoci() throws Exception {
        Pagamento pagamento = new Pagamento();
        pagamento.setCodDominio(DOM);
        pagamento.setIuv(orfana.getIuv());
        pagamento.setIur(orfana.getIur());
        pagamento.setIndiceDati(1);
        pagamento.setImportoPagato(10.0);
        pagamento.setDataPagamento(orfana.getDataMsgRicevuta());
        pagamento.setDataAcquisizione(orfana.getDataMsgRicevuta());
        pagamento.setTipo(TipoPagamento.ENTRATA);
        pagamento.setStato(StatoPagamentoRicevuta.PAGATO);
        pagamento.setRpt(orfana);
        pagamentoRepository.save(pagamento);

        leggi(tuttiIDomini(), ORFANA).andExpect(status().isOk())
                .andExpect(jsonPath("$.iuv", is("IUV-ORFANA")))
                .andExpect(jsonPath("$.pendenza").doesNotExist())
                .andExpect(jsonPath("$._links.pendenza").doesNotExist())
                .andExpect(jsonPath("$._links.rt.href", is(ORFANA + "/rt")))
                .andExpect(jsonPath("$.rt").exists())
                .andExpect(jsonPath("$.voci", hasSize(1)))
                .andExpect(jsonPath("$.voci[0].indice", is(1)));
    }

    @Test
    void laRtSiScaricaELaRptNonEsiste() throws Exception {
        String p = tuttiIDomini();

        mvc.perform(get(ORFANA + "/rt").header(HttpHeaders.ACCEPT, "application/xml").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());
        leggi(p, ORFANA + "/rpt").andExpect(status().isNotFound());
    }

    @Test
    void laStampaUsaLaDescrizioneDellaRtEIlDominioDellaRicevuta() throws Exception {
        String p = tuttiIDomini();

        mvc.perform(get(ORFANA + "/rt").header(HttpHeaders.ACCEPT, "application/pdf").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());

        ArgumentCaptor<Receipt> payload = ArgumentCaptor.forClass(Receipt.class);
        verify(stampeClient).streamReceipt(payload.capture(), any());
        assertThat(payload.getValue().getPaymentSubject()).isEqualTo("Pagamento TARI 2026");
        assertThat(payload.getValue().getOrganization().getFiscalCode()).isEqualTo(DOM);
        assertThat(payload.getValue().getOrganization().getBusinessName()).isEqualTo("Dominio " + DOM);
    }
}
