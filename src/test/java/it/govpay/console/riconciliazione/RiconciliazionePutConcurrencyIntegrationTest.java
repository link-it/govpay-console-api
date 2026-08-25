package it.govpay.console.riconciliazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Verifica la gestione reale della concorrenza su {@code PUT
 * /riconciliazioni/{idDominio}/{id}}: due richieste parallele sulla stessa
 * coppia violano {@code unique_incassi_1} in insert, la seconda deve
 * ri-leggere e rispondere idempotentemente (mai un 500).
 *
 * <p>Volutamente **non** {@code @Transactional}: i due thread worker aprono
 * connessioni/transazioni proprie, indipendenti da quella del thread di
 * test — se la fixture del {@code @BeforeEach} restasse in una transazione
 * di test mai committata, i worker (altra connessione) non la vedrebbero
 * affatto (isolamento standard tra connessioni), a prescindere dalla race
 * che si vuole verificare.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiconciliazionePutConcurrencyIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_CONC = "44444444444";
    private static final String ID_RICONCILIAZIONE = "CONCORRENTE1";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private FrRepository frRepository;
    @Autowired private IncassoRepository incassoRepository;
    @Autowired private AclRepository aclRepository;

    private Dominio dominio;

    @BeforeEach
    void setup() {
        dominio = newDominio(DOM_CONC, "Dominio Concorrenza");
        Fr fr = new Fr();
        fr.setIdDominio(dominio.getId());
        fr.setCodDominio(dominio.getCodDominio());
        fr.setCodFlusso("FLUSSO-CONC");
        fr.setCodPsp("PSP-1");
        fr.setRevisione(1L);
        fr.setStato("ACCETTATA");
        fr.setIur("TRN-FLUSSO-CONC");
        fr.setDataOraFlusso(date(2026, 6, 20));
        fr.setDataAcquisizione(date(2026, 6, 20));
        fr.setNumeroPagamenti(1L);
        fr.setImportoTotalePagamenti(100.0);
        fr.setObsoleto(false);
        frRepository.save(fr);
    }

    @AfterEach
    void cleanup() {
        incassoRepository.findByCodDominioAndIdentificativo(DOM_CONC, ID_RICONCILIAZIONE)
                .ifPresent(incassoRepository::delete);
        frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(DOM_CONC, "FLUSSO-CONC")
                .ifPresent(frRepository::delete);
        dominioRepository.delete(dominio);
    }

    @Test
    void duePutParalleliSullaStessaCoppiaMaiUn500EUnaSolaRiga() throws Exception {
        String principal = utenteScrittura("u-concorrente");
        String body = """
                {"importo": 100.0, "idFlusso": "FLUSSO-CONC"}""";

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> eseguiPut(principal, body, startGate)),
                    pool.submit(() -> eseguiPut(principal, body, startGate)));
            startGate.countDown();

            for (Future<Integer> f : futures) {
                int status = f.get(10, TimeUnit.SECONDS);
                assertThat(status).isIn(200, 202);
            }
        } finally {
            pool.shutdown();
        }

        long righe = incassoRepository.findAll().stream()
                .filter(i -> DOM_CONC.equals(i.getCodDominio()) && ID_RICONCILIAZIONE.equals(i.getIdentificativo()))
                .count();
        assertThat(righe).isEqualTo(1);
    }

    private int eseguiPut(String principal, String body, CountDownLatch startGate) throws Exception {
        startGate.await();
        return mvc.perform(put("/riconciliazioni/" + DOM_CONC + "/" + ID_RICONCILIAZIONE)
                        .with(httpBasic(principal, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteScrittura(String principal) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(true);
        u.setAutorizzazioneTipiVersStar(true);
        u.setRuoli("OPERATORE");
        u.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(u);

        Operatore op = new Operatore();
        op.setNome(principal);
        op.setIdUtenza(u.getId());
        operatoreRepository.save(op);

        Acl acl = new Acl();
        acl.setIdUtenza(u.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("RW");
        aclRepository.save(acl);

        return principal;
    }
}
