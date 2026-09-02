package it.govpay.console.eventi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.console.web.NotFoundException;

/**
 * Verifica la costruzione della URI verso GDE (modalita' offset/cursor,
 * filtri) e la mappatura degli errori HTTP sulle eccezioni applicative.
 * {@link ConfigurazioneService} e' mockato: il comportamento reale del
 * connettore (auth, timeout) e' responsabilita' di govpay-common, non di
 * questo client.
 */
class EventoGdeClientTest {

    private static final String BASE_URL = "http://fake-gde";

    private ConfigurazioneService configurazioneService;
    private MockRestServiceServer server;
    private EventoGdeClient client;

    @BeforeEach
    void setup() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);

        configurazioneService = mock(ConfigurazioneService.class);
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        when(configurazioneService.getServizioGDE()).thenReturn(Connettore.builder().url(BASE_URL).build());
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);

        client = new EventoGdeClient(configurazioneService, new GdeRawClient());
    }

    @Test
    void findEventi_modalitaOffset_costruisceQueryConFiltri() {
        server.expect(requestTo(BASE_URL + "/eventi?limit=25&offset=0&total=true"
                        + "&dataDa=2026-06-01T00:00Z&dataA=2026-06-02T00:00Z"
                        + "&idDominio=12345678901&idDominio=99999999999"
                        + "&iuv=IUV1&componente=API_PENDENZE&categoriaEvento=INTERFACCIA"))
                .andRespond(withSuccess("""
                        {"page":{"offset":0,"limit":25,"total":1,"hasNext":false},"items":[]}""",
                        MediaType.APPLICATION_JSON));

        EventoGdeQuery query = new EventoGdeQuery(
                25, 0, false, null, null, true,
                OffsetDateTime.parse("2026-06-01T00:00:00Z"), OffsetDateTime.parse("2026-06-02T00:00:00Z"),
                List.of("12345678901", "99999999999"),
                "IUV1", null, null, null,
                "API_PENDENZE", "INTERFACCIA", null, null,
                null, null, null, null, null);

        var result = client.findEventi(query);

        assertThat(result.getPage().getTotal()).isEqualTo(1L);
        server.verify();
    }

    @Test
    void findEventi_modalitaCursor_primaPagina_nienteCursorDataId() {
        server.expect(requestTo(BASE_URL + "/eventi?limit=10&pagingMode=CURSOR"))
                .andRespond(withSuccess("""
                        {"page":{"limit":10,"hasNext":false},"items":[]}""", MediaType.APPLICATION_JSON));

        EventoGdeQuery query = new EventoGdeQuery(
                10, 0, true, null, null, false,
                null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        client.findEventi(query);
        server.verify();
    }

    @Test
    void findEventi_modalitaCursor_paginaSuccessiva_includeCursorDataId() {
        OffsetDateTime cursorData = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        server.expect(requestTo(BASE_URL + "/eventi?limit=10&pagingMode=CURSOR"
                        + "&cursorData=2026-06-01T10:00Z&cursorId=42"))
                .andRespond(withSuccess("""
                        {"page":{"limit":10,"hasNext":false},"items":[]}""", MediaType.APPLICATION_JSON));

        EventoGdeQuery query = new EventoGdeQuery(
                10, 0, true, cursorData, 42L, false,
                null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        client.findEventi(query);
        server.verify();
    }

    @Test
    void findEventi_servizioNonAbilitato_lanciaGdeNonConfiguratoException() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);

        EventoGdeQuery query = new EventoGdeQuery(
                25, 0, false, null, null, false, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> client.findEventi(query)).isInstanceOf(GdeNonConfiguratoException.class);
    }

    @Test
    void findEventi_erroreHttpDelServer_diventaGdeNonRaggiungibileException() {
        server.expect(requestTo(BASE_URL + "/eventi?limit=25&offset=0")).andRespond(withServerError());

        EventoGdeQuery query = new EventoGdeQuery(
                25, 0, false, null, null, false, null, null, List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> client.findEventi(query)).isInstanceOf(GdeNonRaggiungibileException.class);
    }

    @Test
    void getEventoById_deserializzaEvento() {
        server.expect(requestTo(BASE_URL + "/eventi/7"))
                .andRespond(withSuccess("""
                        {"id":7,"dataEvento":"2026-07-01T10:00:00Z","componente":"API_PAGOPA",
                         "categoriaEvento":"INTERFACCIA","ruolo":"CLIENT","tipoEvento":"nodoInviaRPT","esito":"OK"}""",
                        MediaType.APPLICATION_JSON));

        var evento = client.getEventoById(7L);

        assertThat(evento.getId()).isEqualTo(7L);
        assertThat(evento.getTipoEvento()).isEqualTo("nodoInviaRPT");
        server.verify();
    }

    @Test
    void getEventoById_404delServer_diventaNotFoundException() {
        server.expect(requestTo(BASE_URL + "/eventi/999")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getEventoById(999L)).isInstanceOf(NotFoundException.class);
        server.verify();
    }

    @Test
    void getEventoById_erroreHttpDelServer_diventaGdeNonRaggiungibileException() {
        server.expect(requestTo(BASE_URL + "/eventi/1")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getEventoById(1L)).isInstanceOf(GdeNonRaggiungibileException.class);
    }
}
