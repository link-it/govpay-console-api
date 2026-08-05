package it.govpay.console.eventi;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.entity.UtenzaDominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaDominioRepository;
import it.govpay.console.repository.UtenzaRepository;
import it.govpay.console.web.NotFoundException;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.RuoloEvento;

/**
 * Integration test di {@code GET /eventi/{id}} (issue #51, step 2):
 * metadata-only, ACL post-fetch, {@code _links}. {@link EventoGdeClient} e'
 * mockato (comportamento HTTP gia' verificato in {@link EventoGdeClientTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventoDetailIntegrationTest {

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

    @MockitoBean
    private EventoGdeClient eventoGdeClient;

    private Dominio domA;

    @BeforeEach
    void setup() {
        domA = newDominio(DOM_A, "Dominio A");
        newDominio(DOM_B, "Dominio B");
    }

    @Test
    void dettaglio_200_conLinksECacheControl() throws Exception {
        String p = utenteDominiStar("u-detail");
        when(eventoGdeClient.getEventoById(7L)).thenReturn(new Evento()
                .id(7L)
                .dataEvento(OffsetDateTime.parse("2026-07-01T10:00:00Z"))
                .componente(ComponenteEvento.API_PAGOPA)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.CLIENT)
                .tipoEvento("nodoInviaRPT")
                .esito(EsitoEvento.OK)
                .idDominio(DOM_A)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(java.util.List.of(new Header().nome("Content-Type").valore("application/xml")))
                        .payload("aGVsbG8=")));

        mvc.perform(get("/eventi/7").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Cache-Control"))
                .andExpect(jsonPath("$.id", is(7)))
                .andExpect(jsonPath("$.componente", is("API_PAGOPA")))
                .andExpect(jsonPath("$._links.self.href", is("/eventi/7")))
                .andExpect(jsonPath("$._links.richiesta.href", is("/eventi/7/richiesta")))
                .andExpect(jsonPath("$._links.risposta").doesNotExist())
                .andExpect(jsonPath("$._links.dominio.href", is("/domini/" + DOM_A)))
                .andExpect(jsonPath("$.contentTypeRichiesta", is("application/xml")))
                .andExpect(jsonPath("$.parametriRichiesta").doesNotExist());
    }

    @Test
    void dettaglio_conFrCorrelato_linkFlussoPresente() throws Exception {
        String p = utenteDominiStar("u-flusso");
        Fr fr = new Fr();
        fr.setIdDominio(domA.getId());
        fr.setCodDominio(DOM_A);
        fr.setCodFlusso("FLUSSO-1");
        fr.setCodPsp("PSP-1");
        fr.setRevisione(1L);
        fr.setStato("ACCETTATA");
        fr.setIur("TRN-1");
        fr.setDataOraFlusso(OffsetDateTime.now());
        fr.setDataAcquisizione(OffsetDateTime.now());
        fr = frRepository.save(fr);

        when(eventoGdeClient.getEventoById(20L)).thenReturn(new Evento()
                .id(20L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("acquisizioneFR")
                .esito(EsitoEvento.OK)
                .idDominio(DOM_A)
                .idFr(fr.getId()));

        mvc.perform(get("/eventi/20").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.flusso.href",
                        is("/flussi-rendicontazione/" + DOM_A + "/FLUSSO-1/PSP-1/1")));
    }

    @Test
    void dettaglio_evento_nonVisibilePerAcl_ritorna404() throws Exception {
        String p = utenteSoloDominio("u-fuoriacl", domA);
        when(eventoGdeClient.getEventoById(7L)).thenReturn(new Evento()
                .id(7L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("batch")
                .esito(EsitoEvento.OK)
                .idDominio(DOM_B)); // fuori dal dominio visibile dell'operatore

        mvc.perform(get("/eventi/7").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglio_eventoDiSistemaSenzaDominio_visibileSoloATuttiIDomini() throws Exception {
        when(eventoGdeClient.getEventoById(eq(8L))).thenReturn(new Evento()
                .id(8L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("batch")
                .esito(EsitoEvento.OK)); // idDominio assente

        String pRestricted = utenteSoloDominio("u-ristretto", domA);
        mvc.perform(get("/eventi/8").with(httpBasic(pRestricted, PASSWORD)))
                .andExpect(status().isNotFound());

        String pStar = utenteDominiStar("u-star");
        mvc.perform(get("/eventi/8").with(httpBasic(pStar, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void dettaglio_idNonEsistente_ritorna404() throws Exception {
        String p = utenteDominiStar("u-404");
        when(eventoGdeClient.getEventoById(999L)).thenThrow(new NotFoundException("Evento non trovato: 999"));

        mvc.perform(get("/eventi/999").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dettaglio_filtroNonSupportato_ritorna400() throws Exception {
        String p = utenteDominiStar("u-400");
        mvc.perform(get("/eventi/7?unmask=true").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dettaglio_gdeNonRaggiungibile_ritorna502() throws Exception {
        String p = utenteDominiStar("u-502");
        when(eventoGdeClient.getEventoById(7L))
                .thenThrow(new GdeNonRaggiungibileException("giu'", new RuntimeException()));

        mvc.perform(get("/eventi/7").with(httpBasic(p, PASSWORD)))
                .andExpect(status().isBadGateway());
    }

    // ----- helpers ---------------------------------------------------------------------

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
