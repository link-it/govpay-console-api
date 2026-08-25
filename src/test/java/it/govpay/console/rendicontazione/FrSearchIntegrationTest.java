package it.govpay.console.rendicontazione;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Acl;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Rendicontazione;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.AclRepository;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.RendicontazioneRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;

/**
 * Integration test della collection {@code GET /flussi-rendicontazione}
 * (issue #50, PR 70a): filtri, sort, paginazione offset/cursor e ACL. Nessuna
 * modifica allo schema: tutto letto da {@code fr}/{@code rendicontazioni} come
 * gia' esistenti in V1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrSearchIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";
    private static final String DOM_B = "22222222222";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;
    @Autowired private FrRepository frRepository;
    @Autowired private RendicontazioneRepository rendicontazioneRepository;
    @Autowired private AclRepository aclRepository;

    private Dominio domA;
    private Dominio domB;
    private Fr a1Rev1Obsoleto;
    private Fr a1Rev2;
    private Fr a2Anomalo;
    private Fr a3Incassato;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        // dominio A: 4 righe. FLUSSO-A1 riemesso (rev1 obsoleta, rev2 valida).
        a1Rev1Obsoleto = newFr(domA, "FLUSSO-A1", "PSP-1", 1L, "ACCETTATA", true,
                date(2026, 6, 18), date(2026, 6, 18), null, 5L, 100.0);
        a1Rev2 = newFr(domA, "FLUSSO-A1", "PSP-1", 2L, "ACCETTATA", false,
                date(2026, 6, 22), date(2026, 6, 22), null, 5L, 100.0);
        a2Anomalo = newFr(domA, "FLUSSO-A2", "PSP-1", 1L, "ANOMALA", false,
                date(2026, 6, 19), date(2026, 6, 19), null, 2L, 20.0);
        a3Incassato = newFr(domA, "FLUSSO-A3", "PSP-2", 1L, "ACCETTATA", false,
                date(2026, 6, 20), date(2026, 6, 20), 999L, 3L, 50.0);

        // dominio B: 2 righe.
        newFr(domB, "FLUSSO-B1", "PSP-3", 1L, "RIFIUTATA", false,
                date(2026, 6, 18), date(2026, 6, 18), null, 1L, 10.0);
        newFr(domB, "FLUSSO-B2", "PSP-3", 1L, "ACCETTATA", false,
                date(2026, 6, 21), date(2026, 6, 21), null, 4L, 40.0);

        // riga di rendicontazione per il filtro iuv, agganciata a FLUSSO-A2.
        Rendicontazione rnd = new Rendicontazione();
        rnd.setIdFr(a2Anomalo.getId());
        rnd.setIuv("IUV-XYZ");
        rnd.setData(date(2026, 6, 19));
        rendicontazioneRepository.save(rnd);
    }

    // ----- summary shape -------------------------------------------------------

    @Test
    void summaryEspoineICampiAttesi() throws Exception {
        String p = utenteDominiStar("u-shape");
        mvc.perform(get("/flussi-rendicontazione?idFlusso=FLUSSO-A2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idDominio", is(DOM_A)))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A2")))
                .andExpect(jsonPath("$.results[0].idPsp", is("PSP-1")))
                .andExpect(jsonPath("$.results[0].revisione", is(1)))
                .andExpect(jsonPath("$.results[0].dataOraFlusso").exists())
                .andExpect(jsonPath("$.results[0].dataAcquisizione").exists())
                .andExpect(jsonPath("$.results[0].stato", is("ANOMALO")))
                .andExpect(jsonPath("$.results[0].numeroPagamenti", is(2)))
                .andExpect(jsonPath("$.results[0].importoTotale", is(20.0)));
    }

    // ----- filtri ---------------------------------------------------------------

    @Test
    void filtroIdDominio() throws Exception {
        String p = utenteDominiStar("u-dom");
        mvc.perform(get("/flussi-rendicontazione?idDominio=" + DOM_B).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    void filtroIdFlusso() throws Exception {
        String p = utenteDominiStar("u-flusso");
        mvc.perform(get("/flussi-rendicontazione?idFlusso=FLUSSO-A1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2))); // le 2 revisioni
    }

    @Test
    void filtroIdPsp() throws Exception {
        String p = utenteDominiStar("u-psp");
        mvc.perform(get("/flussi-rendicontazione?idPsp=PSP-2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A3")));
    }

    @Test
    void filtroDataRangeInclusivoSuDataAcquisizione() throws Exception {
        String p = utenteDominiStar("u-data");
        // 19 -> 20 incluso: A2 (19), A3 (20). Esclude A1r1/B1 (18), A1r2 (22), B2 (21).
        mvc.perform(get("/flussi-rendicontazione?dataDa=2026-06-19T00:00:00Z&dataA=2026-06-20T23:59:59Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idFlusso", contains("FLUSSO-A3", "FLUSSO-A2")));
    }

    @Test
    void filtroStatoObsoletoPrevaleSulRaw() throws Exception {
        String p = utenteDominiStar("u-obs");
        mvc.perform(get("/flussi-rendicontazione?stato=OBSOLETO").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A1")))
                .andExpect(jsonPath("$.results[0].revisione", is(1)));
    }

    @Test
    void filtroStatoAcquisitoEscludeObsoleti() throws Exception {
        String p = utenteDominiStar("u-acq");
        mvc.perform(get("/flussi-rendicontazione?stato=ACQUISITO&idDominio=" + DOM_A)
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                // ACCETTATA non obsolete su A: rev2 di A1 e A3 (non A1 rev1, obsoleta)
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idFlusso", contains("FLUSSO-A1", "FLUSSO-A3")));
    }

    @Test
    void filtroIncassato() throws Exception {
        String p = utenteDominiStar("u-inc");
        mvc.perform(get("/flussi-rendicontazione?incassato=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A3")));
    }

    @Test
    void filtroIuvViaJoinRendicontazioni() throws Exception {
        String p = utenteDominiStar("u-iuv");
        mvc.perform(get("/flussi-rendicontazione?iuv=IUV-XYZ").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].idFlusso", is("FLUSSO-A2")));
    }

    @Test
    void filtroNonSupportatoRitorna400() throws Exception {
        String p = utenteDominiStar("u-400f");
        mvc.perform(get("/flussi-rendicontazione?escludiObsoleti=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- sort -------------------------------------------------------------------

    @Test
    void sortDefaultDataOraFlussoDesc() throws Exception {
        String p = utenteDominiStar("u-sortd");
        mvc.perform(get("/flussi-rendicontazione?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idFlusso",
                        contains("FLUSSO-A1", "FLUSSO-A3", "FLUSSO-A2", "FLUSSO-A1")));
    }

    @Test
    void sortEsplicitoAscendente() throws Exception {
        String p = utenteDominiStar("u-sorta");
        mvc.perform(get("/flussi-rendicontazione?idDominio=" + DOM_A + "&sort=dataOraFlusso")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].idFlusso",
                        contains("FLUSSO-A1", "FLUSSO-A2", "FLUSSO-A3", "FLUSSO-A1")));
    }

    @Test
    void sortCampoSconosciutoRitorna400() throws Exception {
        String p = utenteDominiStar("u-sortx");
        mvc.perform(get("/flussi-rendicontazione?sort=-idFlusso").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- paginazione --------------------------------------------------------------

    @Test
    void offsetConTotale() throws Exception {
        String p = utenteDominiStar("u-off");
        mvc.perform(get("/flussi-rendicontazione?limit=2&total=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasNextPage", is(true)))
                .andExpect(jsonPath("$.pagination.totalResults", is(6)))
                .andExpect(jsonPath("$.pagination.totalPages", is(3)));
    }

    @Test
    void cursorPrimaPaginaEPaginaSuccessiva() throws Exception {
        String p = utenteDominiStar("u-cur");
        String body = mvc.perform(get("/flussi-rendicontazione?cursor=&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();

        String next = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
        mvc.perform(get("/flussi-rendicontazione?cursor=" + next + "&limit=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void pageECursorMutuamenteEsclusiviRitorna400() throws Exception {
        String p = utenteDominiStar("u-mutex");
        mvc.perform(get("/flussi-rendicontazione?cursor=&page=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitOltre200Ritorna400() throws Exception {
        String p = utenteDominiStar("u-lim");
        mvc.perform(get("/flussi-rendicontazione?limit=5000").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- ACL -------------------------------------------------------------------------

    @Test
    void aclLimitaAiDominiVisibili() throws Exception {
        String p = utenteSoloDominio("u-acl", domB);
        mvc.perform(get("/flussi-rendicontazione").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[*].idDominio", contains(DOM_B, DOM_B)));
    }

    @Test
    void listaSenzaDirittoServizioRitorna403() throws Exception {
        String p = utenteSenzaDirittoServizio("u-noacl-list");
        mvc.perform(get("/flussi-rendicontazione").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ----- fixture helpers -----------------------------------------------------------

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private Fr newFr(Dominio dominio, String codFlusso, String codPsp, Long revisione,
                     String stato, boolean obsoleto, OffsetDateTime dataOraFlusso,
                     OffsetDateTime dataAcquisizione, Long idIncasso,
                     Long numeroPagamenti, Double importoTotale) {
        Fr fr = new Fr();
        fr.setIdDominio(dominio.getId());
        fr.setCodDominio(dominio.getCodDominio());
        fr.setCodFlusso(codFlusso);
        fr.setCodPsp(codPsp);
        fr.setRevisione(revisione);
        fr.setStato(stato);
        fr.setDescrizioneStato(stato.equals("ANOMALA") ? "flusso anomalo" : null);
        fr.setIur("TRN-" + codFlusso + "-" + revisione);
        fr.setDataOraFlusso(dataOraFlusso);
        fr.setDataRegolamento(dataOraFlusso.plusDays(1));
        fr.setDataAcquisizione(dataAcquisizione);
        fr.setNumeroPagamenti(numeroPagamenti);
        fr.setImportoTotalePagamenti(importoTotale);
        fr.setObsoleto(obsoleto);
        fr.setIdIncasso(idIncasso);
        return frRepository.save(fr);
    }

    private static OffsetDateTime date(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private String utenteDominiStar(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grantLettura(u);
        return principal;
    }

    private String utenteSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        grantLettura(u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    /** Come {@link #utenteDominiStar} ma senza il diritto di lettura sul servizio: per i test 403. */
    private String utenteSenzaDirittoServizio(String principal) {
        Utenza u = baseUtenza(principal, true, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private void grantLettura(Utenza utenza) {
        Acl acl = new Acl();
        acl.setIdUtenza(utenza.getId());
        acl.setServizio(AclServizio.RENDICONTAZIONI_E_INCASSI.getValue());
        acl.setDiritti("R");
        aclRepository.save(acl);
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini, boolean tuttiTipi) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
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
}
