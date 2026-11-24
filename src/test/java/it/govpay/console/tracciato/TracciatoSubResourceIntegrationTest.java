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
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.OperazioneRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

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
    private ConsoleDominioRepository dominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;
    @Autowired
    private TracciatoRepository tracciatoRepository;
    @Autowired
    private OperazioneRepository operazioneRepository;
    @Autowired
    private ConsoleApplicazioneRepository applicazioneRepository;
    @PersistenceContext private EntityManager em;
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
                        {"idVocePendenza": "V1", "importo": 10.0, "descrizione": "Voce di test", "stato": "NON_ESEGUITO", "codEntrata": "TARI"}
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

    private Operazione nuovaOperazione(Long idTracciato, long numero, String tipo, String stato, Pendenza v) {
        Tracciato tracciato = tracciatoRepository.findById(idTracciato).orElseThrow();
        Operazione op = new Operazione();
        op.setTracciato(tracciato);
        op.setTipoOperazione(tipo);
        op.setLineaElaborazione(numero);
        op.setStato(stato);
        op.setCodVersamentoEnte("PEND-00" + numero);
        op.setCodDominio("12345678901");
        op.setPendenza(v);
        op.setDatiRichiesta(("{\"idA2A\":\"A2A_TEST\",\"idPendenza\":\"PEND-00" + numero
                + "\",\"idDominio\":\"12345678901\",\"importo\":10.0,\"causale\":\"c\",\"voci\":[],"
                + "\"idA2A\":\"A2A_TEST\"}").getBytes(StandardCharsets.UTF_8));
        op.setDatiRisposta(("{\"stato\":\"ESEGUITO\",\"tipoOperazione\":\"ADD\",\"esito\":\"ADD_OK\","
                + "\"descrizioneEsito\":\"ok\",\"numero\":" + numero
                + ",\"idA2A\":\"A2A_TEST\",\"idPendenza\":\"PEND-00" + numero + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        return operazioneRepository.save(op);
    }

    private Pendenza nuovoVersamento(String codVersamentoEnte, Applicazione app) {
        TipoVersamento tipoVersamento = new TipoVersamento();
        tipoVersamento.setCodTipoVersamento("TARI-" + codVersamentoEnte);
        tipoVersamento.setDescrizione("TARI Fixture");
        tipoVersamentoRepository.save(tipoVersamento);

        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tipoVersamento);
        tipoVersamentoDominioRepository.save(tvd);

        Pendenza v = PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), dominio.getId(),
                tipoVersamento.getId(), tvd.getId(), null, codVersamentoEnte, "011111111111111111", "RSSMRA80A01H501U", 10.0,
                StatoPendenza.ESEGUITO, 0));
        v.setImportoPagato(10.0);
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataUltimoAggiornamento(OffsetDateTime.now());
        v.getOpzionePagamento().getPdDebitore().getDebitore().setAnagrafica("Mario Rossi");
        return v;
    }

    @Test
    void listaOperazioniSoloMetadataNessunAudit() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-1");
        applicazioneRepository.save(app);
        Pendenza v = nuovoVersamento("PEND-001", app);
        nuovaOperazione(id, 1, "ADD", "ESEGUITO_OK", v);
        nuovaOperazione(id, 2, "DEL", "ESEGUITO_KO", null);
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].stato", is("ESEGUITO")))
                .andExpect(jsonPath("$.results[1].stato", is("SCARTATO")))
                .andExpect(jsonPath("$.results[0].soggettoPagatore").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima);
    }

    /**
     * Regressione: {@code operazioni.stato} nel DB porta i valori grezzi V1
     * ({@code ESEGUITO_OK}/{@code ESEGUITO_KO}), non i nomi semantici esposti
     * dall'API ({@code ESEGUITO}/{@code SCARTATO}). Il filtro deve tradurre,
     * non confrontare alla lettera.
     */
    @Test
    void filtraPerStatoTraduceIlValoreGrezzo() throws Exception {
        Long id = uploadTracciato();
        nuovaOperazione(id, 1, "ADD", "ESEGUITO_OK", null);
        nuovaOperazione(id, 2, "DEL", "ESEGUITO_KO", null);
        nuovaOperazione(id, 3, "ADD", "NON_VALIDO", null);

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").param("stato", "ESEGUITO")
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].numero", is(1)));

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").param("stato", "SCARTATO")
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].numero", is(2)));
    }

    /**
     * Regressione: {@code operazioni.tipo_operazione} nel DB puo' valere sia
     * {@code N_V} sia {@code INC} per una riga non valida (V1 li tratta come
     * sinonimi, stesso ramo default) — entrambi devono confluire nel filtro
     * {@code tipoOperazione=NON_VALIDA}.
     */
    @Test
    void filtraPerTipoOperazioneNonValidaCopreEntrambiIRawValues() throws Exception {
        Long id = uploadTracciato();
        nuovaOperazione(id, 1, "N_V", "NON_VALIDO", null);
        nuovaOperazione(id, 2, "INC", "NON_VALIDO", null);
        nuovaOperazione(id, 3, "ADD", "ESEGUITO_OK", null);

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").param("tipoOperazione", "NON_VALIDA")
                        .with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].numero", org.hamcrest.Matchers.contains(1, 2)));
    }

    @Test
    void dettaglioOperazioneConSoggettoEAudit() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-2");
        applicazioneRepository.save(app);
        Pendenza v = nuovoVersamento("PEND-001", app);
        nuovaOperazione(id, 1, "ADD", "ESEGUITO_OK", v);
        long auditPrima = gpAuditRepository.count();

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni/1").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soggettoPagatore.identificativo", is("RSSMRA80A01H501U")))
                .andExpect(jsonPath("$.risposta.esito", is("ADD_OK")));

        org.assertj.core.api.Assertions.assertThat(gpAuditRepository.count()).isEqualTo(auditPrima + 1);
    }

    /** Una pendenza senza opzione di pagamento non ha debitore: l'operazione si legge, senza soggetto. */
    @Test
    void dettaglioOperazioneDiPendenzaSenzaOpzioneNonHaIlSoggetto() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-3");
        applicazioneRepository.save(app);
        TipoVersamento tipoVersamento = new TipoVersamento();
        tipoVersamento.setCodTipoVersamento("TARI-SENZA-OPZ");
        tipoVersamento.setDescrizione("TARI Fixture");
        tipoVersamentoRepository.save(tipoVersamento);
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tipoVersamento);
        tipoVersamentoDominioRepository.save(tvd);
        Pendenza p = PendenzaV3Fixtures.pendenzaSenzaOpzione(em, new Dati(app.getId(), dominio.getId(),
                tipoVersamento.getId(), tvd.getId(), null, "PEND-SENZA-OPZ", "011111111111111119",
                "RSSMRA80A01H501U", 10.0, StatoPendenza.ESEGUITO, 0));
        nuovaOperazione(id, 1, "ADD", "ESEGUITO_OK", p);

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni/1").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroAvviso", is("011111111111111119")))
                .andExpect(jsonPath("$.soggettoPagatore").doesNotExist());
    }

    @Test
    void listaOperazioniRiportaIlNumeroAvvisoDellaPendenza() throws Exception {
        Long id = uploadTracciato();
        Applicazione app = new Applicazione();
        app.setCodApplicazione("APP-SUB-4");
        applicazioneRepository.save(app);
        Pendenza v = nuovoVersamento("PEND-LISTA", app);
        nuovaOperazione(id, 1, "ADD", "ESEGUITO_OK", v);

        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].numeroAvviso", is("011111111111111111")));
    }

    @Test
    void dettaglioOperazioneInesistenteReturns404() throws Exception {
        Long id = uploadTracciato();
        mvc.perform(get("/pendenze/tracciati/" + id + "/operazioni/999").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
