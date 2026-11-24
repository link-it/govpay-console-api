package it.govpay.console.avviso;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Applicazione;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.TipoVersamento;
import it.govpay.console.entity.TipoVersamentoDominio;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.ConsoleApplicazioneRepository;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TipoVersamentoRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.pendenza.PendenzaV3Fixtures;
import it.govpay.console.pendenza.PendenzaV3Fixtures.Dati;
import it.govpay.pendenze.model.StatoPendenza;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import it.govpay.common.auth.GovpayPasswordEncoder;

/**
 * Verifica che con {@code app.stampe.base-url} vuoto la branch PDF risponda 503
 * e non chiami il microservizio. Test in context separato perche' la property
 * cambia il bean wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.stampe.base-url="})
@Transactional
class AvvisoNotConfiguredTest {

    private static final String PRINCIPAL = "operatore-503";
    private static final String PASSWORD = "secret";
    private static final String APP_COD = "APP-503";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private ConsoleDominioRepository dominioRepository;
    @Autowired
    private ConsoleApplicazioneRepository applicazioneRepository;
    @Autowired
    private TipoVersamentoRepository tipoVersamentoRepository;
    @Autowired
    private TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore 503");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Dominio domA = new Dominio();
        domA.setCodDominio("11111111111");
        domA.setRagioneSociale("Comune 503");
        domA.setAuxDigit(0);
        dominioRepository.save(domA);

        Applicazione app = new Applicazione();
        app.setCodApplicazione(APP_COD);
        applicazioneRepository.save(app);

        TipoVersamento tv = new TipoVersamento();
        tv.setCodTipoVersamento("TARI");
        tv.setDescrizione("TARI");
        tipoVersamentoRepository.save(tv);

        TipoVersamentoDominio tvd = new TipoVersamentoDominio();
        tvd.setDominio(domA);
        tvd.setTipoVersamento(tv);
        tipoVersamentoDominioRepository.save(tvd);

        PendenzaV3Fixtures.pendenza(em, new Dati(app.getId(), domA.getId(), tv.getId(), tvd.getId(), null,
                "PEND-503", "012345678901234567", "XXX", 50.0, StatoPendenza.NON_ESEGUITO, 0));
    }

    @Test
    void returns503WhenStampeBaseUrlIsBlank() throws Exception {
        mvc.perform(get("/pendenze/" + APP_COD + "/PEND-503/avviso")
                        .accept(MediaType.APPLICATION_PDF)
                        .with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"));
    }
}
