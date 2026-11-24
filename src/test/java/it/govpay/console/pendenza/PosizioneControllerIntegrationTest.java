package it.govpay.console.pendenza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.GpAudit;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.common.auth.GovpayPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PosizioneControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore-pos";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-POS";
    private static final String DOM_VISIBILE = "77777777777";
    private static final String DOM_INVISIBILE = "88888888888";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext private EntityManager em;
    @Autowired private AclRepository aclRepository;
    @Autowired private GpAuditRepository gpAuditRepository;

    private Dominio domVisibile;
    private Dominio domInvisibile;
    private TipoVersamento tvVisibile;
    private TipoVersamento tvInvisibile;
    private TipoVersamentoDominio tvdVisibile;
    private TipoVersamentoDominio tvdInvisibile;
    private Applicazione app;
    private Operatore operatore;

    @BeforeEach
    void setup() {
        Utenza u = new Utenza();
        u.setPrincipal(PRINCIPAL);
        u.setPrincipalOriginale(PRINCIPAL);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(false);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        operatore = new Operatore();
        operatore.setNome("Op Pos");
        operatore.setIdUtenza(u.getId());
        operatoreRepository.save(operatore);

        domVisibile = new Dominio();
        domVisibile.setCodDominio(DOM_VISIBILE);
        domVisibile.setRagioneSociale("Comune visibile");
        domVisibile.setAuxDigit(0);
        dominioRepository.save(domVisibile);

        domInvisibile = new Dominio();
        domInvisibile.setCodDominio(DOM_INVISIBILE);
        domInvisibile.setRagioneSociale("Comune invisibile");
        domInvisibile.setAuxDigit(0);
        dominioRepository.save(domInvisibile);

        UtenzaDominio ud = new UtenzaDominio();
        ud.setIdUtenza(u.getId());
        ud.setIdDominio(domVisibile.getId());
        utenzaDominioRepository.save(ud);

        app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        tvVisibile = new TipoVersamento();
        tvVisibile.setCodTipoVersamento("TARI");
        tvVisibile.setDescrizione("TARI");
        tipoVersamentoRepository.save(tvVisibile);

        tvInvisibile = new TipoVersamento();
        tvInvisibile.setCodTipoVersamento("IMU");
        tvInvisibile.setDescrizione("IMU");
        tipoVersamentoRepository.save(tvInvisibile);

        UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
        utv.setIdUtenza(u.getId());
        utv.setIdTipoVersamento(tvVisibile.getId());
        utenzaTipoVersamentoRepository.save(utv);

        tvdVisibile = new TipoVersamentoDominio();
        tvdVisibile.setDominio(domVisibile);
        tvdVisibile.setTipoVersamento(tvVisibile);
        tipoVersamentoDominioRepository.save(tvdVisibile);

        tvdInvisibile = new TipoVersamentoDominio();
        tvdInvisibile.setDominio(domVisibile);
        tvdInvisibile.setTipoVersamento(tvInvisibile);
        tipoVersamentoDominioRepository.save(tvdInvisibile);

        // ACL minima per la lettura
        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setRuolo("OPERATORE");
        acl.setServizio("PENDENZE");
        acl.setDiritti("R");
        aclRepository.save(acl);
    }

    private String iupd(String idPendenza) {
        return APP_COD + DOM_VISIBILE + "DOC-" + idPendenza;
    }

    private Dati dati(String idPendenza, Dominio dominio, TipoVersamento tv, TipoVersamentoDominio tvd,
                      String debitore) {
        return new Dati(app.getId(), dominio.getId(), tv.getId(), tvd.getId(), null, idPendenza,
                null, debitore, 100.0, StatoPendenza.NON_ESEGUITO, 0);
    }

    private String url(String idPendenza) {
        return "/posizioni-debitorie/" + APP_COD + "/" + iupd(idPendenza);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(url("P-1"))).andExpect(status().isUnauthorized());
    }

    @Test
    void restituisceDebitoriOpzioniEPendenze() throws Exception {
        Pendenza p = PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em,
                dati("POS-1", domVisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"), "VRDLGI90B02H501T", false);
        em.flush();
        p.getOpzionePagamento().getPdDebitore().getDebitore().setEmail("mario.rossi@example.com");

        mvc.perform(get(url("POS-1")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idA2A", is(APP_COD)))
                .andExpect(jsonPath("$.idPosizioneDebitoria", is(iupd("POS-1"))))
                .andExpect(jsonPath("$.dominio.idDominio", is(DOM_VISIBILE)))
                .andExpect(jsonPath("$.descrizione", is("Posizione POS-1")))
                .andExpect(jsonPath("$.debitori", hasSize(2)))
                .andExpect(jsonPath("$.debitori[0].soggetto.identificativo", is("VRDLGI90B02H501T")))
                .andExpect(jsonPath("$.debitori[0].rimosso", is(false)))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento", hasSize(0)))
                .andExpect(jsonPath("$.debitori[1].soggetto.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.debitori[1].soggetto.email", is("mario.rossi@example.com")))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento", hasSize(1)))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].tipologia", is("SOLUZIONE_UNICA")))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].stato", is("DISPONIBILE")))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].sendAbilitato", is(false)))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].pendenze", hasSize(1)))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].pendenze[0].idPendenza", is("POS-1")))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].pendenze[0].posizioneDebitoria.idPosizioneDebitoria",
                        is(iupd("POS-1"))));
    }

    @Test
    void debitoreRimossoCompareContrassegnato() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em,
                dati("POS-RIM", domVisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"), "VRDLGI90B02H501T", true);

        mvc.perform(get(url("POS-RIM")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitori[0].rimosso", is(false)))
                .andExpect(jsonPath("$.debitori[1].rimosso", is(true)))
                .andExpect(jsonPath("$.debitori[1].opzioniPagamento[0].stato", is("ANNULLATA")));
    }

    @Test
    void ilPianoRatealeRaggruppaLeRateNellaStessaOpzione() throws Exception {
        Pendenza prima = PendenzaV3Fixtures.pendenza(em, dati("POS-RATE", domVisibile, tvVisibile, tvdVisibile,
                "RSSMRA80A01H501U").conTipologia(TipologiaOpzionePagamento.PIANO_RATEALE));
        Pendenza seconda = new Pendenza();
        seconda.setIdApplicazione(prima.getIdApplicazione());
        seconda.setIdDominio(prima.getIdDominio());
        seconda.setIdTipoVersamento(prima.getIdTipoVersamento());
        seconda.setIdTipoPendenza(prima.getIdTipoPendenza());
        seconda.setIdPendenza("POS-RATE-2");
        seconda.setNumeroRata(2);
        seconda.setImporto(50.0);
        seconda.setStato(StatoPendenza.NON_ESEGUITO);
        seconda.setDebitoreIdentificativo("RSSMRA80A01H501U");
        seconda.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        seconda.setDataCreazione(prima.getDataCreazione());
        seconda.setDataUltimoAggiornamento(prima.getDataUltimoAggiornamento());
        prima.getOpzionePagamento().addPendenza(seconda);
        em.persist(seconda);

        mvc.perform(get(url("POS-RATE")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento", hasSize(1)))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].tipologia", is("PIANO_RATEALE")))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].pendenze", hasSize(2)))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].pendenze[0].idPendenza", is("POS-RATE")))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].pendenze[1].idPendenza", is("POS-RATE-2")));
    }

    @Test
    void lePendenzeDiTipiNonVisibiliSonoOmesse() throws Exception {
        Pendenza tari = PendenzaV3Fixtures.pendenza(em,
                dati("POS-MISTA", domVisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"));
        PendenzaV3Fixtures.pendenzaNellaStessaPosizione(em, tari,
                dati("POS-MISTA-IMU", domVisibile, tvInvisibile, tvdInvisibile, "RSSMRA80A01H501U"));

        mvc.perform(get(url("POS-MISTA")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debitori", hasSize(1)))
                .andExpect(jsonPath("$.debitori[0].soggetto.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento", hasSize(1)))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].pendenze", hasSize(1)))
                .andExpect(jsonPath("$.debitori[0].opzioniPagamento[0].pendenze[0].idPendenza", is("POS-MISTA")));
    }

    @Test
    void returns404SeNessunaPendenzaEVisibile() throws Exception {
        PendenzaV3Fixtures.pendenza(em, dati("POS-IMU", domVisibile, tvInvisibile, tvdInvisibile, "RSSMRA80A01H501U"));

        mvc.perform(get(url("POS-IMU")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404SeIlDominioNonEVisibile() throws Exception {
        PendenzaV3Fixtures.pendenza(em, dati("POS-DOM", domInvisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"));

        mvc.perform(get("/posizioni-debitorie/" + APP_COD + "/" + APP_COD + DOM_INVISIBILE + "DOC-POS-DOM")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404PerPosizioneInesistenteOMalformata() throws Exception {
        mvc.perform(get(url("INESISTENTE")).with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
        mvc.perform(get("/posizioni-debitorie/" + APP_COD + "/MALFORMATA").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void scriveAuditSoloPerIl200() throws Exception {
        Pendenza visibile = PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em,
                dati("POS-AUD", domVisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"), "VRDLGI90B02H501T", false);
        Pendenza nascosta = PendenzaV3Fixtures.pendenza(em,
                dati("POS-AUD-NO", domInvisibile, tvVisibile, tvdVisibile, "RSSMRA80A01H501U"));
        em.flush();
        Long idPosizione = visibile.getOpzionePagamento().getPosizioneDebitoria().getId();
        Long idNascosta = nascosta.getOpzionePagamento().getPosizioneDebitoria().getId();

        mvc.perform(get(url("POS-AUD")).header("X-Forwarded-For", "1.2.3.4")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/posizioni-debitorie/" + APP_COD + "/" + APP_COD + DOM_INVISIBILE + "DOC-POS-AUD-NO")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());

        List<GpAudit> nostri = gpAuditRepository.findAll().stream()
                .filter(a -> PosizioneService.AZIONE_AUDIT_VISUALIZZA.equals(a.getTipoOggetto())
                        && (idPosizione.equals(a.getIdOggetto()) || idNascosta.equals(a.getIdOggetto())))
                .toList();
        assertThat(nostri).hasSize(1);
        GpAudit riga = nostri.get(0);
        assertThat(riga.getIdOggetto()).isEqualTo(idPosizione);
        assertThat(riga.getIdOperatore()).isEqualTo(operatore.getId());
        assertThat(riga.getIpRichiedente()).isEqualTo("1.2.3.4");
        assertThat(riga.getOggetto())
                .contains("\"idA2A\":\"" + APP_COD + "\"")
                .contains("\"idPosizioneDebitoria\":\"" + iupd("POS-AUD") + "\"")
                .contains("VRDLGI90B02H501T")
                .contains("RSSMRA80A01H501U");
    }
}
