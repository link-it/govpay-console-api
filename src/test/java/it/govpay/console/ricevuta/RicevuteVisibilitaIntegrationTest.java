package it.govpay.console.ricevuta;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.entity.UtenzaTipoVersamento;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.repository.UtenzaTipoVersamentoRepository;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.Rpt;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Visibilita' e filtri delle ricevute sul modello v3: la unita' operativa sta sulla posizione
 * della pendenza (assente vale come {@code EC}), il tipo sulla pendenza, e una ricevuta la cui
 * pendenza non ha opzione di pagamento resta leggibile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RicevuteVisibilitaIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM = "33333333333";
    private static final byte[] XML_RT = "<RT><id>fake</id></RT>".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private UtenzaTipoVersamentoRepository utenzaTipoVersamentoRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private ConsoleDominioRepository dominioRepository;
    @Autowired private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @Autowired private UnitaOperativaRepository unitaOperativaRepository;
    @Autowired private ConsoleRptRepository rptRepository;
    @PersistenceContext private EntityManager em;

    private Dominio dominio;
    private UnitaOperativa uoX;
    private UnitaOperativa uoY;
    private UnitaOperativa uoEc;
    private TipoVersamento tari;
    private TipoVersamento imu;
    private TipoVersamentoDominio tariTvd;
    private TipoVersamentoDominio imuTvd;
    private Applicazione app;

    @BeforeEach
    void setup() {
        dominio = new Dominio();
        dominio.setCodDominio(DOM);
        dominio.setRagioneSociale("Dominio ricevute");
        dominio.setAuxDigit(0);
        dominioRepository.save(dominio);

        uoX = uo("UO-X");
        uoY = uo("UO-Y");
        uoEc = uo("EC");

        app = new Applicazione();
        app.setCodApplicazione("APP-VIS");
        applicazioneRepository.save(app);

        tari = tipo("TARI");
        imu = tipo("IMU");
        tariTvd = tipoDominio(tari);
        imuTvd = tipoDominio(imu);

        // pendenza -> UO, tipo
        pendenzaConRicevuta("UOX-TARI", uoX, tari, tariTvd);
        pendenzaConRicevuta("UOY-TARI", uoY, tari, tariTvd);
        pendenzaConRicevuta("UOX-IMU", uoX, imu, imuTvd);
        pendenzaConRicevuta("NOUO-TARI", null, tari, tariTvd);
    }

    private UnitaOperativa uo(String cod) {
        UnitaOperativa uo = new UnitaOperativa();
        uo.setCodUo(cod);
        uo.setUoDenominazione(cod);
        uo.setDominio(dominio);
        return unitaOperativaRepository.save(uo);
    }

    private TipoVersamento tipo(String cod) {
        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento(cod);
        tv.setDescrizione(cod);
        return tipoVersamentoRepository.save(tv);
    }

    private TipoVersamentoDominio tipoDominio(TipoVersamento tv) {
        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(dominio);
        tvd.setTipoVersamento(tv);
        return tipoVersamentoDominioRepository.save(tvd);
    }

    private Dati dati(String idPendenza, UnitaOperativa uo, TipoVersamento tv, TipoVersamentoDominio tvd) {
        return new Dati(app.getId(), dominio.getId(), tv.getId(), tvd.getId(), uo != null ? uo.getId() : null,
                idPendenza, null, "RSSMRA80A01H501U", 100.0, StatoPendenza.ESEGUITO, 0);
    }

    private Pendenza pendenzaConRicevuta(String idPendenza, UnitaOperativa uo, TipoVersamento tv,
                                         TipoVersamentoDominio tvd) {
        Pendenza p = PendenzaV3Fixtures.pendenza(em, dati(idPendenza, uo, tv, tvd));
        ricevuta(p, idPendenza);
        return p;
    }

    private void ricevuta(Pendenza p, String idPendenza) {
        em.flush();
        OffsetDateTime data = OffsetDateTime.now();
        Rpt r = new Rpt();
        r.setCodStazione("STAZIONE-TEST");
        r.setCodMsgRichiesta("MSG-RICHIESTA");
        r.setDataAggiornamentoStato(data);
        r.setIuv("IUV-" + idPendenza);
        r.setIur("CCP-" + idPendenza);
        r.setCodDominio(DOM);
        r.setXmlRt(XML_RT);
        r.setCodEsitoPagamento(0);
        r.setImportoTotalePagato(100.0);
        r.setDataMsgRichiesta(data.minusHours(1));
        r.setDataMsgRicevuta(data);
        r.setIdVersamento(p.getId());
        r.setVersione("SANP_240");
        r.setStato("RT_ACCETTATA_PA");
        rptRepository.save(r);
    }

    private String utente(String principal, boolean tuttiITipi, UnitaOperativa uo, TipoVersamento tipoVisibile) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(false);
        u.setAutorizzazioneTipiVersStar(tuttiITipi);
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
        if (uo != null) {
            link.setIdUo(uo.getId());
        }
        utenzaDominioRepository.save(link);
        if (tipoVisibile != null) {
            UtenzaTipoVersamento utv = new UtenzaTipoVersamento();
            utv.setIdUtenza(u.getId());
            utv.setIdTipoVersamento(tipoVisibile.getId());
            utenzaTipoVersamentoRepository.save(utv);
        }
        return principal;
    }

    private org.springframework.test.web.servlet.ResultActions lista(String principal, String query) throws Exception {
        return mvc.perform(get("/ricevute" + query).with(httpBasic(principal, PASSWORD)));
    }

    @Test
    void dominioInteroVedeTutteLeRicevute() throws Exception {
        String p = utente("vis-intero", true, null, null);

        lista(p, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder(
                        "IUV-UOX-TARI", "IUV-UOY-TARI", "IUV-UOX-IMU", "IUV-NOUO-TARI")));
    }

    @Test
    void unaUoSpecificaVedeSoloLeRicevuteDellaSuaUo() throws Exception {
        String p = utente("vis-uox", true, uoX, null);

        lista(p, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("IUV-UOX-TARI", "IUV-UOX-IMU")));
    }

    @Test
    void laUoEcVedeLeRicevuteDellePendenzeSenzaUo() throws Exception {
        String p = utente("vis-ec", true, uoEc, null);

        lista(p, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("IUV-NOUO-TARI")));
    }

    @Test
    void ilTipoRestrittivoLimitaLeRicevute() throws Exception {
        String p = utente("vis-tari", false, null, tari);

        lista(p, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder(
                        "IUV-UOX-TARI", "IUV-UOY-TARI", "IUV-NOUO-TARI")));
    }

    @Test
    void ilDettaglioDiUnaRicevutaFuoriDallaUoDaNotFound() throws Exception {
        String p = utente("vis-uox", true, uoX, null);

        mvc.perform(get("/ricevute/" + DOM + "/IUV-UOY-TARI/CCP-UOY-TARI").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/ricevute/" + DOM + "/IUV-UOX-TARI/CCP-UOX-TARI").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendenza.idPendenza", is("UOX-TARI")));
    }

    @Test
    void ilFiltroPerUoUsaLaUoDellaPosizione() throws Exception {
        String p = utente("vis-intero", true, null, null);

        lista(p, "?idUnitaOperativa=UO-Y").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].iuv", is("IUV-UOY-TARI")));
    }

    @Test
    void ilFiltroPerDebitoreUsaIlDebitoreDellOpzione() throws Exception {
        String p = utente("vis-intero", true, null, null);

        lista(p, "?identificativoDebitore=rssmra80a01h501u").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(4)));
        lista(p, "?identificativoDebitore=ALTRO").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    // --- pendenza senza opzione di pagamento ---

    private Pendenza pendenzaSenzaOpzione(String idPendenza) {
        Pendenza p = PendenzaV3Fixtures.pendenzaSenzaOpzione(em, dati(idPendenza, null, tari, tariTvd));
        ricevuta(p, idPendenza);
        return p;
    }

    @Test
    void laRicevutaDiUnaPendenzaSenzaOpzioneResta200() throws Exception {
        pendenzaSenzaOpzione("SENZA-OPZ");
        String p = utente("vis-intero", true, null, null);

        mvc.perform(get("/ricevute/" + DOM + "/IUV-SENZA-OPZ/CCP-SENZA-OPZ").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendenza.idA2A", is("APP-VIS")))
                .andExpect(jsonPath("$.pendenza.idPendenza", is("SENZA-OPZ")))
                .andExpect(jsonPath("$._links.pendenza.href", is("/pendenze/APP-VIS/SENZA-OPZ")));
        lista(p, "?idPendenza=SENZA-OPZ").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)));
        mvc.perform(get("/pendenze/APP-VIS/SENZA-OPZ/ricevute").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    /** Imposta la unita' operativa storica ({@code versamenti.id_uo}) di una pendenza senza opzione. */
    private void uoStorica(Pendenza p, UnitaOperativa uo) {
        em.flush();
        em.createNativeQuery("update versamenti set id_uo = :uo where id = :id")
                .setParameter("uo", uo.getId()).setParameter("id", p.getId()).executeUpdate();
    }

    /** Una pendenza senza opzione ha ancora la sua unita' operativa storica: non vale come EC. */
    @Test
    void unaPendenzaStoricaMantieneLaSuaUo() throws Exception {
        uoStorica(pendenzaSenzaOpzione("STORICA-X"), uoX);
        String uox = utente("vis-uox", true, uoX, null);
        String ec = utente("vis-ec", true, uoEc, null);
        String intero = utente("vis-intero", true, null, null);
        String dettaglio = "/ricevute/" + DOM + "/IUV-STORICA-X/CCP-STORICA-X";

        mvc.perform(get(dettaglio).with(httpBasic(ec, PASSWORD))).andExpect(status().isNotFound());
        lista(ec, "?idPendenza=STORICA-X").andExpect(status().isOk()).andExpect(jsonPath("$.results", empty()));
        mvc.perform(get("/pendenze/APP-VIS/STORICA-X/ricevute").with(httpBasic(ec, PASSWORD)))
                .andExpect(status().isNotFound());

        mvc.perform(get(dettaglio).with(httpBasic(uox, PASSWORD))).andExpect(status().isOk());
        lista(uox, "?idPendenza=STORICA-X").andExpect(status().isOk()).andExpect(jsonPath("$.results", hasSize(1)));
        mvc.perform(get(dettaglio).with(httpBasic(intero, PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void unaPendenzaStoricaConUoEcLaVedeChiHaLaUoEc() throws Exception {
        uoStorica(pendenzaSenzaOpzione("STORICA-EC"), uoEc);
        String uox = utente("vis-uox", true, uoX, null);
        String ec = utente("vis-ec", true, uoEc, null);

        lista(ec, "?idPendenza=STORICA-EC").andExpect(status().isOk()).andExpect(jsonPath("$.results", hasSize(1)));
        lista(uox, "?idPendenza=STORICA-EC").andExpect(status().isOk()).andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void ilFiltroPerUoUsaLaUoStoricaSenzaOpzione() throws Exception {
        uoStorica(pendenzaSenzaOpzione("STORICA-X"), uoX);
        String p = utente("vis-intero", true, null, null);

        lista(p, "?idUnitaOperativa=UO-X").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder("IUV-UOX-TARI", "IUV-UOX-IMU", "IUV-STORICA-X")));
    }

    /** Il filtro {@code EC} include le pendenze senza unita' operativa, come la visibilita'. */
    @Test
    void ilFiltroEcIncludeLePosizioniSenzaUo() throws Exception {
        pendenzaSenzaOpzione("SENZA-OPZ");
        uoStorica(pendenzaSenzaOpzione("STORICA-EC"), uoEc);
        String p = utente("vis-intero", true, null, null);

        lista(p, "?idUnitaOperativa=EC").andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].iuv", containsInAnyOrder(
                        "IUV-NOUO-TARI", "IUV-SENZA-OPZ", "IUV-STORICA-EC")));
    }

    @Test
    void unaPendenzaSenzaOpzioneNonHaUoEValeComeEc() throws Exception {
        pendenzaSenzaOpzione("SENZA-OPZ");
        String uox = utente("vis-uox", true, uoX, null);
        String ec = utente("vis-ec", true, uoEc, null);

        lista(uox, "?idPendenza=SENZA-OPZ").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
        lista(ec, "?idPendenza=SENZA-OPZ").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)));
    }

    @Test
    void ifiltriSulDebitoreNonTrovanoLaPendenzaSenzaOpzione() throws Exception {
        pendenzaSenzaOpzione("SENZA-OPZ");
        String p = utente("vis-intero", true, null, null);

        lista(p, "?idPendenza=SENZA-OPZ&identificativoDebitore=RSSMRA80A01H501U").andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }
}
