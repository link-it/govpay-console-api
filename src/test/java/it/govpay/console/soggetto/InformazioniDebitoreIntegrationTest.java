package it.govpay.console.soggetto;

import static org.assertj.core.api.Assertions.assertThat;
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
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.common.auth.GovpayPasswordEncoder;

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

    /** Pendenza v3 con il debitore completo di anagrafica e contatti. */
    private Pendenza newPendenza(String idPendenza, Dominio dominio,
                                 TipoVersamento tv, TipoVersamentoDominio tvd) {
        Pendenza p = PendenzaV3Fixtures.pendenza(em, dati(idPendenza, dominio, tv, tvd));
        completaDebitore(p);
        return p;
    }

    private Dati dati(String idPendenza, Dominio dominio, TipoVersamento tv, TipoVersamentoDominio tvd) {
        return new Dati(app.getId(), dominio.getId(), tv.getId(), tvd.getId(), null, idPendenza,
                "012345678901234567", "RSSMRA80A01H501U", 100.0, StatoPendenza.NON_ESEGUITO, 0);
    }

    private static void completaDebitore(Pendenza p) {
        Debitore d = p.getOpzionePagamento().getPdDebitore().getDebitore();
        d.setAnagrafica("Mario Rossi");
        d.setIndirizzo("Via Roma");
        d.setCivico("1");
        d.setCap("00100");
        d.setLocalita("Roma");
        d.setProvincia("RM");
        d.setNazione("IT");
        d.setEmail("mario.rossi@example.com");
        d.setCellulare("3331234567");
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
        Pendenza p = newPendenza("PEND-MINIMAL", domVisibile, tvVisibile, tvdVisibile);
        Debitore d = p.getOpzionePagamento().getPdDebitore().getDebitore();
        d.setIndirizzo(null);
        d.setCivico(null);
        d.setCap(null);
        d.setLocalita(null);
        d.setProvincia(null);
        d.setEmail(null);
        d.setCellulare(null);

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
        Pendenza hidden = newPendenza("PEND-HIDDEN", domInvisibile, tvVisibile, tvdVisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-HIDDEN/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());

        // Scoping su idOggetto di questa pendenza: l'audit (REQUIRES_NEW) committa e
        // si accumula nell'H2 condiviso, quindi un assert globale sarebbe ordine-dipendente.
        assertThat(gpAuditRepository.findAll())
                .as("nessun audit per 404 ACL")
                .filteredOn(a -> InformazioniDebitoreService.AZIONE_AUDIT_VISUALIZZA
                        .equals(a.getTipoOggetto())
                        && hidden.getId().equals(a.getIdOggetto()))
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
        Pendenza v = newPendenza("PEND-AUDIT", domVisibile, tvVisibile, tvdVisibile);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-AUDIT/informazioniDebitore")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk());

        // Scoping su idOggetto = id della pendenza di questo test: l'azione è scritta
        // anche da altri test (es. fixture E2E) e gli audit committano (REQUIRES_NEW),
        // accumulandosi nell'H2 condiviso → un filtro per sola azione sarebbe fragile.
        List<GpAudit> tutti = gpAuditRepository.findAll();
        List<GpAudit> nostri = tutti.stream()
                .filter(a -> InformazioniDebitoreService.AZIONE_AUDIT_VISUALIZZA
                        .equals(a.getTipoOggetto())
                        && v.getId().equals(a.getIdOggetto()))
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

    @Test
    void debitoreDellaPendenzaNonIlPrimoDellaPosizione() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em,
                dati("PEND-SECONDO", domVisibile, tvVisibile, tvdVisibile), "VRDLGI90B02H501T", false);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SECONDO/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo", is("G")))
                .andExpect(jsonPath("$.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.anagrafica", is("Anagrafica RSSMRA80A01H501U")));
    }

    @Test
    void debitoreRimossoRispondeComeIlDettaglio() throws Exception {
        PendenzaV3Fixtures.pendenzaDelSecondoDebitore(em,
                dati("PEND-RIMOSSO", domVisibile, tvVisibile, tvdVisibile), "VRDLGI90B02H501T", true);

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-RIMOSSO/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificativo", is("RSSMRA80A01H501U")));
    }

    @Test
    void returns404WhenPendenzaSenzaOpzione() throws Exception {
        PendenzaV3Fixtures.pendenzaSenzaOpzione(em, dati("PEND-SENZA-OPZIONE", domVisibile, tvVisibile, tvdVisibile));

        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-SENZA-OPZIONE/informazioniDebitore")
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
