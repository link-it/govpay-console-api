package it.govpay.console.tracciato;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Operazione;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.OperazioneRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.VersamentoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TracciatoSubResourceIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String SERVIZIO_PENDENZE = "Pendenze";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private AclRepository aclRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;
    @Autowired
    private TracciatoRepository tracciatoRepository;
    @Autowired
    private OperazioneRepository operazioneRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private VersamentoRepository versamentoRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    private static final AtomicBoolean ZIP_STAMPE_COLUMN_READY = new AtomicBoolean(false);

    private Dominio dominio;
    private String principal;

    @BeforeEach
    void setup() {
        addZipStampeColumnOnce();

        dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune Fixture Sub-resource");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        principal = "op-sub-resource";
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);

        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(SERVIZIO_PENDENZE);
        acl.setDiritti("RW");
        aclRepository.save(acl);

    }

    /**
     * {@code zip_stampe} non e' mappato da nessuna entity JPA (vedi
     * {@link TracciatoStampeService}), quindi {@code ddl-auto=create-drop}
     * non lo crea: va aggiunto qui per i test. Eseguito su una connessione
     * JDBC presa direttamente dal {@link DataSource}, fuori dalla gestione
     * transazionale di Spring Test: un {@code ALTER TABLE} (DDL, fa
     * auto-commit) eseguito con lo stesso {@code jdbcTemplate}/connessione
     * sincronizzata da {@code @Transactional} commetterebbe anticipatamente
     * anche i dati di fixture gia' scritti in quella transazione, facendoli
     * sopravvivere al rollback di fine test. Una sola volta per l'intera
     * classe (colonna gia' presente altrimenti).
     */
    private void addZipStampeColumnOnce() {
        if (!ZIP_STAMPE_COLUMN_READY.compareAndSet(false, true)) {
            return;
        }
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()) {
            s.execute("ALTER TABLE tracciati ADD COLUMN IF NOT EXISTS zip_stampe BYTEA");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Long uploadTracciato() throws Exception {
        String body = """
                {
                  "idDominio": "12345678901",
                  "inserimenti": [
                    {
                      "idDominio": "12345678901",
                      "importo": 10.0,
                      "causale": "Causale di test",
                      "idA2A": "A2A_TEST",
                      "idPendenza": "PEND-001",
                      "voci": [
                        {"idVocePendenza": "V1", "importo": 10.0, "descrizione": "Voce di test", "stato": "NON_PAGATA", "codEntrata": "TARI"}
                      ]
                    }
                  ]
                }
                """;
        String location = mvc.perform(post("/pendenze/tracciati").with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    // ----- /richiesta -----------------------------------------------------------

    @Test
    void richiestaReturns200EScriveAudit() throws Exception {
        Long id = uploadTracciato();
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/richiesta").with(httpBasic(principal, PASSWORD))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(jsonPath("$.idDominio", is("12345678901")));

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 1);
    }

    @Test
    void richiestaConAcceptIncompatibileReturns406() throws Exception {
        Long id = uploadTracciato();
        mvc.perform(get("/pendenze/tracciati/" + id + "/richiesta").with(httpBasic(principal, PASSWORD))
                        .accept("text/csv"))
                .andExpect(status().isNotAcceptable());
    }

    // ----- /esito -----------------------------------------------------------------

    @Test
    void esitoNonAncoraDisponibileReturns404() throws Exception {
        Long id = uploadTracciato();
        mvc.perform(get("/pendenze/tracciati/" + id + "/esito").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void esitoDisponibileReturns200SenzaAudit() throws Exception {
        Long id = uploadTracciato();
        Tracciato tracciato = tracciatoRepository.findById(id).orElseThrow();
        tracciato.setRawEsito("{\"idTracciato\":\"x\",\"inserimenti\":[],\"annullamenti\":[]}"
                .getBytes(StandardCharsets.UTF_8));
        tracciatoRepository.save(tracciato);
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/esito").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima);
    }

    // ----- /stampe -----------------------------------------------------------------

    @Test
    void stampeNonDisponibiliReturns404() throws Exception {
        Long id = uploadTracciato();
        mvc.perform(get("/pendenze/tracciati/" + id + "/stampe").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void stampeDisponibiliReturns200Zip() throws Exception {
        Long id = uploadTracciato();
        jdbcTemplate.update("UPDATE tracciati SET zip_stampe = ? WHERE id = ?",
                "fake-zip-content".getBytes(StandardCharsets.UTF_8), id);

        mvc.perform(get("/pendenze/tracciati/" + id + "/stampe").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    // ----- /operazioni --------------------------------------------------------------

    private Operazione nuovaOperazione(Long idTracciato, long numero, String tipo, String stato, Versamento v) {
        Tracciato tracciato = tracciatoRepository.findById(idTracciato).orElseThrow();
        Operazione op = new Operazione();
        op.setTracciato(tracciato);
        op.setTipoOperazione(tipo);
        op.setLineaElaborazione(numero);
        op.setStato(stato);
        op.setCodVersamentoEnte("PEND-00" + numero);
        op.setCodDominio("12345678901");
        op.setVersamento(v);
        op.setDatiRichiesta(("{\"idA2A\":\"A2A_TEST\",\"idPendenza\":\"PEND-00" + numero
                + "\",\"idDominio\":\"12345678901\",\"importo\":10.0,\"causale\":\"c\",\"voci\":[],"
                + "\"idA2A\":\"A2A_TEST\"}").getBytes(StandardCharsets.UTF_8));
        op.setDatiRisposta(("{\"stato\":\"ESEGUITO\",\"tipoOperazione\":\"ADD\",\"esito\":\"ADD_OK\","
                + "\"descrizioneEsito\":\"ok\",\"numero\":" + numero
                + ",\"idA2A\":\"A2A_TEST\",\"idPendenza\":\"PEND-00" + numero + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return operazioneRepository.save(op);
    }

    private Versamento nuovoVersamento(String codVersamentoEnte, Applicazione app) {
        TipoVersamento tipoVersamento = new TipoVersamento();
        tipoVersamento.setCodTipoVersamento("TARI-" + codVersamentoEnte);
        tipoVersamento.setDescrizione("TARI Fixture");
        tipoVersamentoRepository.save(tipoVersamento);

        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tipoVersamento);
        tipoVersamentoDominioRepository.save(tvd);

        Versamento v = new Versamento();
        v.setCodVersamentoEnte(codVersamentoEnte);
        v.setImportoTotale(10.0);
        v.setImportoPagato(10.0);
        v.setStatoVersamento("ESEGUITO");
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        v.setSrcDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setAnomalo(false);
        v.setAck(true);
        v.setTipo("DOVUTO");
        v.setDominio(dominio);
        v.setApplicazione(app);
        v.setTipoVersamento(tipoVersamento);
        v.setTipoVersamentoDominio(tvd);
        v.setNumeroAvviso("011111111111111111");
        return versamentoRepository.save(v);
    }

    @Test
    void listaOperazioniSoloMetadataNessunAudit() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-1");
        applicazioneRepository.save(app);
        Versamento v = nuovoVersamento("PEND-001", app);
        nuovaOperazione(id, 1, "ADD", "ESEGUITO", v);
        nuovaOperazione(id, 2, "DEL", "SCARTATO", null);
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].soggettoPagatore").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima);
    }

    @Test
    void dettaglioOperazioneConSoggettoEAudit() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-2");
        applicazioneRepository.save(app);
        Versamento v = nuovoVersamento("PEND-001", app);
        nuovaOperazione(id, 1, "ADD", "ESEGUITO", v);
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni/1").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soggettoPagatore.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.risposta.esito", is("ADD_OK")));

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 1);
    }

    @Test
    void dettaglioOperazioneInesistenteReturns404() throws Exception {
        Long id = uploadTracciato();
        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni/999").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
