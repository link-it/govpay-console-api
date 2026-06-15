package it.govpay.console.soggetto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.GovpayPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InformazioniDebitoreIntegrationTest {

    private static final String PRINCIPAL = "operatore-f";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-F";
    private static final String DOM_VISIBILE = "55555555555";
    private static final String DOM_INVISIBILE = "66666666666";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired private ApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private VersamentoRepository versamentoRepository;
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
        operatore.setNome("Op F");
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

    private Versamento newPendenza(String idPendenza, Dominio dominio,
                                   TipoVersamento tv, TipoVersamentoDominio tvd) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setImportoPagato(0.0);
        v.setStatoVersamento("NON_ESEGUITO");
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDebitoreTipo("F");
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setDebitoreIndirizzo("Via Roma");
        v.setDebitoreCivico("1");
        v.setDebitoreCap("00100");
        v.setDebitoreLocalita("Roma");
        v.setDebitoreProvincia("RM");
        v.setDebitoreNazione("IT");
        v.setDebitoreEmail("mario.rossi@example.com");
        v.setDebitoreCellulare("3331234567");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setIuvVersamento("1234567890123");
        v.setNumeroAvviso("012345678901234567");
        v.setDominio(dominio);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        return versamentoRepository.save(v);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/informazioniDebitore"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsSoggettoCompleto() throws Exception {
        newPendenza("PEND-1", domVisibile, tvVisibile, tvdVisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-1/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tipo", is("F")))
                .andExpect(jsonPath("$.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.anagrafica", is("Mario Rossi")))
                .andExpect(jsonPath("$.indirizzo", is("Via Roma")))
                .andExpect(jsonPath("$.civico", is("1")))
                .andExpect(jsonPath("$.cap", is("00100")))
                .andExpect(jsonPath("$.localita", is("Roma")))
                .andExpect(jsonPath("$.provincia", is("RM")))
                .andExpect(jsonPath("$.nazione", is("IT")))
                .andExpect(jsonPath("$.email", is("mario.rossi@example.com")))
                .andExpect(jsonPath("$.cellulare", is("3331234567")));
    }

    @Test
    void returnsSoggettoConCampiOpzionaliNulli() throws Exception {
        Versamento v = newPendenza("PEND-MINIMAL", domVisibile, tvVisibile, tvdVisibile);
        v.setDebitoreIndirizzo(null);
        v.setDebitoreCivico(null);
        v.setDebitoreCap(null);
        v.setDebitoreLocalita(null);
        v.setDebitoreProvincia(null);
        v.setDebitoreEmail(null);
        v.setDebitoreCellulare(null);
        versamentoRepository.save(v);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-MINIMAL/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.anagrafica", is("Mario Rossi")))
                .andExpect(jsonPath("$.indirizzo").doesNotExist())
                .andExpect(jsonPath("$.cap").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void returns404WhenPendenzaDoesNotExist() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/UNKNOWN/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void returns404AntiLeakWhenDominioNotVisible() throws Exception {
        newPendenza("PEND-HIDDEN", domInvisibile, tvVisibile, tvdVisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-HIDDEN/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());

        assertThat(gpAuditRepository.findAll())
                .as("nessun audit per 404 ACL")
                .filteredOn(a -> InformazioniDebitoreService.AZIONE_AUDIT_VISUALIZZA
                        .equals(a.getTipoOggetto()))
                .isEmpty();
    }

    @Test
    void returns404AntiLeakWhenTipoVersamentoNotVisible() throws Exception {
        newPendenza("PEND-IMU", domVisibile, tvInvisibile, tvdInvisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-IMU/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void scriveAuditDopo200() throws Exception {
        Versamento v = newPendenza("PEND-AUDIT", domVisibile, tvVisibile, tvdVisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-AUDIT/informazioniDebitore")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        List<GpAudit> tutti = gpAuditRepository.findAll();
        List<GpAudit> nostri = tutti.stream()
                .filter(a -> InformazioniDebitoreService.AZIONE_AUDIT_VISUALIZZA
                        .equals(a.getTipoOggetto()))
                .toList();
        assertThat(nostri).hasSize(1);
        GpAudit row = nostri.get(0);
        assertThat(row.getIdOggetto()).isEqualTo(v.getId());
        assertThat(row.getIdOperatore()).isEqualTo(operatore.getId());
        assertThat(row.getIpRichiedente()).isEqualTo("1.2.3.4");
        assertThat(row.getOggetto())
                .contains("\"identificativoDebitore\":\"RSSMRA80A01H501U\"")
                .contains("\"idA2A\":\"" + APP_COD + "\"")
                .contains("\"idPendenza\":\"PEND-AUDIT\"");
    }
}
