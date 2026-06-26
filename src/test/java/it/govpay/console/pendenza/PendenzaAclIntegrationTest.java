package it.govpay.console.pendenza;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.entity.Versamento;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.common.auth.GovpayPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PendenzaAclIntegrationTest {

    private static final String PASSWORD = "secret";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired
    private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private ApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired
    private VersamentoRepository versamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;

    private Dominio domA;
    private UnitaOperativa uoX;
    private UnitaOperativa uoY;
    private TipoVersamento tariType;
    private TipoVersamento imuType;
    private TipoVersamentoDominio tariTvd;
    private TipoVersamentoDominio imuTvd;
    private Applicazione app;

    @BeforeEach
    void setup() {
        domA = new Dominio();
        domA.setCodDominio("11111111111");
        domA.setRagioneSociale("Dominio A");
        domA.setAuxDigit(0);
        dominioRepository.save(domA);

        uoX = new UnitaOperativa();
        uoX.setCodUo("UO-X");
        uoX.setUoDenominazione("Ufficio Tributi");
        uoX.setDominio(domA);
        unitaOperativaRepository.save(uoX);

        uoY = new UnitaOperativa();
        uoY.setCodUo("UO-Y");
        uoY.setUoDenominazione("Polizia Locale");
        uoY.setDominio(domA);
        unitaOperativaRepository.save(uoY);

        app = new Applicazione();
        app.setCodApplicazione("APP-A");
        applicazioneRepository.save(app);

        tariType = newTipoVersamento("TARI", "Tassa Rifiuti");
        imuType = newTipoVersamento("IMU", "Imposta Municipale");
        tariTvd = newTvd(domA, tariType);
        imuTvd = newTvd(domA, imuType);

        // 4 pendenze: combinazioni UO x tipoVersamento
        newPendenza("P-TARI-UOX", uoX, tariType, tariTvd);
        newPendenza("P-TARI-UOY", uoY, tariType, tariTvd);
        newPendenza("P-IMU-UOX", uoX, imuType, imuTvd);
        newPendenza("P-IMU-UOY", uoY, imuType, imuTvd);
        // Una senza UO
        newPendenza("P-TARI-NOUO", null, tariType, tariTvd);
    }

    private TipoVersamentoDominio newTvd(Dominio d, TipoVersamento tv) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(d);
        tvd.setTipoVersamento(tv);
        return tipoVersamentoDominioRepository.save(tvd);
    }

    private TipoVersamento newTipoVersamento(String cod, String desc) {
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento(cod);
        tv.setDescrizione(desc);
        return tipoVersamentoRepository.save(tv);
    }

    private void newPendenza(String idPendenza, UnitaOperativa uo, TipoVersamento tv,
                             TipoVersamentoDominio tvd) {
        Versamento v = new Versamento();
        v.setCodVersamentoEnte(idPendenza);
        v.setImportoTotale(100.0);
        v.setStatoVersamento("NON_ESEGUITO");
        v.setDataCreazione(OffsetDateTime.now());
        v.setDataOraUltimoAggiornamento(OffsetDateTime.now());
        v.setDebitoreIdentificativo("CF1234567890ABCDE");
        v.setDebitoreAnagrafica("Test");
        v.setSrcDebitoreIdentificativo("CF1234567890ABCDE");
        v.setImportoPagato(0.0);
        v.setAnomalo(false);
        v.setAck(false);
        v.setTipo("DOVUTO");
        v.setDominio(domA);
        v.setApplicazione(app);
        v.setTipoVersamento(tv);
        v.setTipoVersamentoDominio(tvd);
        v.setUnitaOperativa(uo);
        versamentoRepository.save(v);
    }

    private String newUtenzaConDominioIntero(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(domA.getId());
        // id_uo = NULL: vede tutto il dominio
        utenzaDominioRepository.save(link);
        return principal;
    }

    private String newUtenzaConUoSpecifica(String principal, UnitaOperativa uo) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(domA.getId());
        link.setIdUo(uo.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private String newUtenzaConTipoVersamentoRestrictivo(String principal, TipoVersamento tv) {
        Utenza u = baseUtenza(principal, false);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(domA.getId());
        utenzaDominioRepository.save(link);
        UtenzaTipoVersamento autv = new UtenzaTipoVersamento();
        autv.setIdUtenza(u.getId());
        autv.setIdTipoVersamento(tv.getId());
        utenzaTipoVersamentoRepository.save(autv);
        return principal;
    }

    private Utenza baseUtenza(String principal, boolean tuttiTipi) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(tuttiTipi);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        return u;
    }

    private void attachOperatore(String nome, Utenza u) {
        Operatore op = new Operatore();
        op.setNome(nome);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);
    }

    // ----- ACL UO ---------------------------------------------------------------

    @Test
    void operatoreConDominioInteroVedeTutto() throws Exception {
        String principal = newUtenzaConDominioIntero("user-intero");
        mvc.perform(get("/pendenze").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(5)));
    }

    @Test
    void operatoreConUoSpecificaVedeSoloQuellaUo() throws Exception {
        String principal = newUtenzaConUoSpecifica("user-uox", uoX);
        mvc.perform(get("/pendenze").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("P-TARI-UOX", "P-IMU-UOX")));
    }

    @Test
    void dettaglioPendenzaInUoNonAutorizzataReturns404() throws Exception {
        String principal = newUtenzaConUoSpecifica("user-uox-2", uoX);
        mvc.perform(get("/pendenze/APP-A/P-TARI-UOY").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglioPendenzaInUoAutorizzataReturns200() throws Exception {
        String principal = newUtenzaConUoSpecifica("user-uox-3", uoX);
        mvc.perform(get("/pendenze/APP-A/P-TARI-UOX").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void pendenzaSenzaUoNonVisibilePerUtenteConUoSpecifica() throws Exception {
        String principal = newUtenzaConUoSpecifica("user-uox-4", uoX);
        mvc.perform(get("/pendenze/APP-A/P-TARI-NOUO").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // ----- ACL tipoVersamento ---------------------------------------------------

    @Test
    void operatoreConTipoVersamentoRestrictivoVedeSoloQuelTipo() throws Exception {
        String principal = newUtenzaConTipoVersamentoRestrictivo("user-tari", tariType);
        mvc.perform(get("/pendenze").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[*].idPendenza",
                        containsInAnyOrder("P-TARI-UOX", "P-TARI-UOY", "P-TARI-NOUO")));
    }

    @Test
    void dettaglioPendenzaDiTipoNonAutorizzatoReturns404() throws Exception {
        String principal = newUtenzaConTipoVersamentoRestrictivo("user-tari-2", tariType);
        mvc.perform(get("/pendenze/APP-A/P-IMU-UOX").with(httpBasic(principal, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void utenteSenzaAutorizzazioniRiceveListaVuota() throws Exception {
        Utenza u = baseUtenza("user-nessuna-acl", false);
        utenzaRepository.save(u);
        attachOperatore("user-nessuna-acl", u);
        // Nessuna riga in utenze_domini / utenze_tipo_vers
        mvc.perform(get("/pendenze").with(httpBasic("user-nessuna-acl", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }
}
