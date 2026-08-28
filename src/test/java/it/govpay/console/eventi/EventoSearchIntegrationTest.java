package it.govpay.console.eventi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ListaEventi;
import it.govpay.gde.client.beans.PageInfo;
import it.govpay.gde.client.beans.RuoloEvento;

/**
 * Integration test della collection {@code GET /eventi} (issue #51, step 1):
 * ACL, default temporali, validazioni di costo, paginazione offset/cursor.
 * {@link EventoGdeClient} e' mockato a livello di bean: il comportamento
 * HTTP/deserializzazione verso GDE e' gia' verificato in
 * {@link EventoGdeClientTest}, qui si verifica solo ACL/orchestrazione/mappatura.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventoSearchIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String DOM_A = "11111111111";
    private static final String DOM_B = "22222222222";

    @Autowired private MockMvc mvc;
    @Autowired private GovpayPasswordEncoder encoder;
    @Autowired private UtenzaRepository utenzaRepository;
    @Autowired private UtenzaDominioRepository utenzaDominioRepository;
    @Autowired private OperatoreRepository operatoreRepository;
    @Autowired private DominioRepository dominioRepository;

    @MockitoBean
    private EventoGdeClient eventoGdeClient;

    private Dominio domB;

    @BeforeEach
    void setup() {
        newDominio(DOM_A, "Dominio A");
        domB = newDominio(DOM_B, "Dominio B");

        when(eventoGdeClient.findEventi(any())).thenReturn(emptyResult());
    }

    // ----- shape / mappatura -----------------------------------------------------------

    @Test
    void risultatiVengonoMappatiNelloSummary() throws Exception {
        String p = utenteDominiStar("u-shape");
        Evento evento = new Evento()
                .id(1L)
                .dataEvento(OffsetDateTime.parse("2026-07-01T10:00:00Z"))
                .durataEvento(50L)
                .componente(ComponenteEvento.API_PENDENZE)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("getListaPendenze")
                .esito(EsitoEvento.OK)
                .idDominio(DOM_A);
        when(eventoGdeClient.findEventi(any())).thenReturn(resultWith(evento));

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.results[0].id", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.results[0].componente", org.hamcrest.Matchers.is("API_PENDENZE")))
                .andExpect(jsonPath("$.results[0].categoriaEvento", org.hamcrest.Matchers.is("INTERFACCIA")))
                .andExpect(jsonPath("$.results[0].esito", org.hamcrest.Matchers.is("OK")))
                .andExpect(jsonPath("$.results[0].durataEventoMs", org.hamcrest.Matchers.is(50)));
    }

    // ----- ACL ---------------------------------------------------------------------------

    @Test
    void aclTuttiDomini_nessunFiltroIdDominioInviatoAGde() throws Exception {
        String p = utenteDominiStar("u-star");

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());

        EventoGdeQuery query = catturaQuery();
        assertThat(query.idDominio()).isEmpty();
    }

    @Test
    void aclDominioSingolo_filtraSulDominioVisibile() throws Exception {
        String p = utenteSoloDominio("u-solob", domB);

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());

        EventoGdeQuery query = catturaQuery();
        assertThat(query.idDominio()).containsExactly(DOM_B);
    }

    @Test
    void aclNessunDominioVisibile_listaVuotaSenzaChiamareGde() throws Exception {
        String p = utenteSenzaDomini("u-nodomini");

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(0)));

        verify(eventoGdeClient, never()).findEventi(any());
    }

    @Test
    void filtroIdDominioEsplicitoFuoriAcl_listaVuotaSenzaChiamareGde() throws Exception {
        String p = utenteSoloDominio("u-fuoriacl", domB);

        mvc.perform(get("/eventi?idDominio=" + DOM_A).with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(0)));

        verify(eventoGdeClient, never()).findEventi(any());
    }

    // ----- default temporale ----------------------------------------------------------

    @Test
    void nessunaDataIndicata_forzaDataDaAUltime24Ore() throws Exception {
        String p = utenteDominiStar("u-default24h");
        OffsetDateTime prima = OffsetDateTime.now(ZoneOffset.UTC).minusHours(24).minusMinutes(1);

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());

        EventoGdeQuery query = catturaQuery();
        assertThat(query.dataDa()).isAfter(prima);
        assertThat(query.dataA()).isNull();
    }

    // ----- validazioni di costo ---------------------------------------------------------

    @Test
    void messaggiSenzaRangeEntro7Giorni_ritorna400() throws Exception {
        String p = utenteDominiStar("u-msg400");

        mvc.perform(get("/eventi?messaggi=timeout&dataDa=2020-01-01T00:00:00Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messaggiConRangeEntro7Giorni_ok() throws Exception {
        String p = utenteDominiStar("u-msg200");

        mvc.perform(get("/eventi?messaggi=timeout&dataDa=2026-06-25T00:00:00Z&dataA=2026-06-26T00:00:00Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void totalConRangeOltre24Ore_ritorna400() throws Exception {
        String p = utenteDominiStar("u-tot400");

        mvc.perform(get("/eventi?total=true&dataDa=2020-01-01T00:00:00Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void totalConRangeEntro24Ore_ok() throws Exception {
        String p = utenteDominiStar("u-tot200");
        when(eventoGdeClient.findEventi(any())).thenReturn(
                new ListaEventi().page(new PageInfo().offset(0L).limit(25).total(3L).hasNext(false)).items(java.util.List.of()));

        mvc.perform(get("/eventi?total=true&dataDa=2026-06-25T00:00:00Z&dataA=2026-06-25T23:00:00Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults", org.hamcrest.Matchers.is(3)));
    }

    /**
     * `page` e `items` sono dichiarati obbligatori nello schema di GDE, ma arrivano da
     * una risposta HTTP e sulle risposte non gira alcuna Bean Validation: se GDE ne
     * omette uno, la lista deve degradare, non rispondere 500. E' la tolleranza che
     * giustifica il default dichiarato in `EventoSearchService`, e finora nessun test
     * la verificava.
     */
    @Test
    void rispostaGdeSenzaPageNeItemsDegradaSenzaErrore() throws Exception {
        String p = utenteDominiStar("u-gde-parziale");
        when(eventoGdeClient.findEventi(any())).thenReturn(new ListaEventi());

        mvc.perform(get("/eventi?limit=10").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.pagination.hasNextPage", org.hamcrest.Matchers.is(false)));
    }

    @Test
    void rispostaGdeSenzaPageConTotalRichiestoNonRompe() throws Exception {
        String p = utenteDominiStar("u-gde-nototal");
        when(eventoGdeClient.findEventi(any())).thenReturn(new ListaEventi().items(java.util.List.of()));

        mvc.perform(get("/eventi?total=true&dataDa=2026-06-25T00:00:00Z&dataA=2026-06-25T23:00:00Z")
                        .with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalResults").doesNotExist());
    }

    // ----- paginazione --------------------------------------------------------------------

    @Test
    void offsetConHasNext() throws Exception {
        String p = utenteDominiStar("u-hasnext");
        when(eventoGdeClient.findEventi(any())).thenReturn(
                new ListaEventi().page(new PageInfo().hasNext(true)).items(java.util.List.of()));

        mvc.perform(get("/eventi?limit=10").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.hasNextPage", org.hamcrest.Matchers.is(true)))
                .andExpect(jsonPath("$.pagination.page", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.pagination.limit", org.hamcrest.Matchers.is(10)));
    }

    @Test
    void cursorPrimaPaginaENextCursor() throws Exception {
        String p = utenteDominiStar("u-cursor");
        Evento evento = new Evento().id(9L).dataEvento(OffsetDateTime.parse("2026-07-01T10:00:00Z"))
                .componente(ComponenteEvento.GOVPAY).categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER).tipoEvento("batch").esito(EsitoEvento.OK);
        when(eventoGdeClient.findEventi(any())).thenReturn(
                new ListaEventi().page(new PageInfo().hasNext(true)).items(java.util.List.of(evento)));

        mvc.perform(get("/eventi?cursor=&limit=1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").exists());

        EventoGdeQuery query = catturaQuery();
        assertThat(query.cursorMode()).isTrue();
        assertThat(query.cursorData()).isNull();
    }

    @Test
    void cursorMalformato_ritorna400() throws Exception {
        String p = utenteDominiStar("u-badcursor");

        mvc.perform(get("/eventi?cursor=xxxNONBASE64!!!").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- validazioni generiche ---------------------------------------------------------

    @Test
    void filtroNonSupportato_ritorna400() throws Exception {
        String p = utenteDominiStar("u-400f");
        mvc.perform(get("/eventi?nonEsiste=1").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitOltre100_ritorna400() throws Exception {
        String p = utenteDominiStar("u-lim");
        mvc.perform(get("/eventi?limit=101").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageECursorMutuamenteEsclusivi_ritorna400() throws Exception {
        String p = utenteDominiStar("u-mutex");
        mvc.perform(get("/eventi?cursor=&page=2").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ----- errori GDE ----------------------------------------------------------------------

    @Test
    void gdeNonConfigurato_ritorna503() throws Exception {
        String p = utenteDominiStar("u-503");
        when(eventoGdeClient.findEventi(any())).thenThrow(new GdeNonConfiguratoException());

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void gdeNonRaggiungibile_ritorna502() throws Exception {
        String p = utenteDominiStar("u-502");
        when(eventoGdeClient.findEventi(any()))
                .thenThrow(new GdeNonRaggiungibileException("giu'", new RuntimeException()));

        mvc.perform(get("/eventi").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadGateway());
    }

    // ----- helpers ---------------------------------------------------------------------

    private EventoGdeQuery catturaQuery() {
        ArgumentCaptor<EventoGdeQuery> captor = ArgumentCaptor.forClass(EventoGdeQuery.class);
        verify(eventoGdeClient).findEventi(captor.capture());
        return captor.getValue();
    }

    private static ListaEventi emptyResult() {
        return new ListaEventi().page(new PageInfo().hasNext(false)).items(java.util.List.of());
    }

    private static ListaEventi resultWith(Evento... eventi) {
        return new ListaEventi().page(new PageInfo().hasNext(false)).items(java.util.List.of(eventi));
    }

    private Dominio newDominio(String cod, String ragione) {
        Dominio d = new Dominio();
        d.setCodDominio(cod);
        d.setRagioneSociale(ragione);
        d.setAuxDigit(0);
        return dominioRepository.save(d);
    }

    private String utenteDominiStar(String principal) {
        Utenza u = baseUtenza(principal, true);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private String utenteSoloDominio(String principal, Dominio dominio) {
        Utenza u = baseUtenza(principal, false);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        UtenzaDominio link = new UtenzaDominio();
        link.setIdUtenza(u.getId());
        link.setIdDominio(dominio.getId());
        utenzaDominioRepository.save(link);
        return principal;
    }

    private String utenteSenzaDomini(String principal) {
        Utenza u = baseUtenza(principal, false);
        utenzaRepository.save(u);
        attachOperatore(principal, u);
        return principal;
    }

    private Utenza baseUtenza(String principal, boolean tuttiDomini) {
        Utenza u = new Utenza();
        u.setPrincipal(principal);
        u.setPrincipalOriginale(principal);
        u.setAbilitato(true);
        u.setAutorizzazioneDominiStar(tuttiDomini);
        u.setAutorizzazioneTipiVersStar(true);
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
