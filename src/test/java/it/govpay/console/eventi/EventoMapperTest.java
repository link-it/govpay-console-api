package it.govpay.console.eventi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.Fr;
import it.govpay.console.model.EventoSummary;
import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.DatiPagoPA;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Evento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.RuoloEvento;

class EventoMapperTest {

    private final EventoMapper mapper = new EventoMapper();

    @Test
    void toSummary_mappaTuttiICampi() {
        OffsetDateTime dataEvento = OffsetDateTime.parse("2026-07-01T10:15:30+02:00");
        Evento evento = new Evento()
                .id(42L)
                .dataEvento(dataEvento)
                .durataEvento(77L)
                .componente(ComponenteEvento.API_PENDENZE)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("getListaPendenze")
                .sottotipoEvento("richiesta verso il nodo")
                .esito(EsitoEvento.OK)
                .sottotipoEsito("200")
                .dettaglioEsito("OK")
                .severita(3)
                .idDominio("12345678901")
                .iuv("IUV1")
                .ccp("CCP1")
                .idA2A("A2A1")
                .idPendenza("PEND1")
                .idPagamento("PAG1")
                .transactionId("TX1")
                .clusterId("NODO_1")
                .datiPagoPA(new DatiPagoPA().idPsp("PSP1").idCanale("CAN1"));

        EventoSummary dto = mapper.toSummary(evento);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getDataEvento()).isEqualTo(dataEvento);
        assertThat(dto.getDurataEventoMs()).isEqualTo(77L);
        assertThat(dto.getComponente()).isEqualTo(it.govpay.console.model.ComponenteEvento.API_PENDENZE);
        assertThat(dto.getCategoriaEvento()).isEqualTo(it.govpay.console.model.CategoriaEvento.INTERFACCIA);
        assertThat(dto.getRuolo()).isEqualTo(it.govpay.console.model.RuoloEvento.SERVER);
        assertThat(dto.getTipoEvento()).isEqualTo("getListaPendenze");
        assertThat(dto.getSottotipoEvento()).isEqualTo("richiesta verso il nodo");
        assertThat(dto.getEsito()).isEqualTo(it.govpay.console.model.EsitoEvento.OK);
        assertThat(dto.getSottotipoEsito()).isEqualTo("200");
        assertThat(dto.getDettaglioEsito()).isEqualTo("OK");
        assertThat(dto.getSeverita()).isEqualTo(3);
        assertThat(dto.getIdDominio()).isEqualTo("12345678901");
        assertThat(dto.getIuv()).isEqualTo("IUV1");
        assertThat(dto.getCcp()).isEqualTo("CCP1");
        assertThat(dto.getIdA2A()).isEqualTo("A2A1");
        assertThat(dto.getIdPendenza()).isEqualTo("PEND1");
        assertThat(dto.getIdPagamento()).isEqualTo("PAG1");
        assertThat(dto.getTransactionId()).isEqualTo("TX1");
        assertThat(dto.getClusterId()).isEqualTo("NODO_1");
        assertThat(dto.getDatiPagoPA().getIdPsp()).isEqualTo("PSP1");
        assertThat(dto.getDatiPagoPA().getIdCanale()).isEqualTo("CAN1");
    }

    @Test
    void toSummary_campiOpzionaliAssenti_restanoNull() {
        Evento evento = new Evento()
                .id(1L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.CLIENT)
                .tipoEvento("batch")
                .esito(EsitoEvento.OK);

        EventoSummary dto = mapper.toSummary(evento);

        assertThat(dto.getDatiPagoPA()).isNull();
        assertThat(dto.getIdDominio()).isNull();
        assertThat(dto.getDurataEventoMs()).isNull();
    }

    @Test
    void toDetail_conRichiestaERisposta_popolaMetaELinks() {
        Evento evento = new Evento()
                .id(7L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.API_PAGOPA)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.CLIENT)
                .tipoEvento("nodoInviaRPT")
                .esito(EsitoEvento.OK)
                .idDominio("12345678901")
                .idA2A("A2A1")
                .idPendenza("PEND1")
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().nome("Content-Type").valore("application/xml"),
                                new Header().nome("Authorization").valore("Basic xxx")))
                        .payload("aGVsbG8=")) // "hello" -> 5 byte
                .parametriRisposta(new DettaglioRisposta()
                        .headers(List.of(new Header().nome("content-type").valore("application/soap+xml")))
                        .payload("aGVsbG8gd29ybGQ=")); // "hello world" -> 11 byte

        it.govpay.console.model.Evento dto = mapper.toDetail(evento, null);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getContentTypeRichiesta()).isEqualTo("application/xml");
        assertThat(dto.getDimensioneRichiesta()).isEqualTo(5L);
        assertThat(dto.getNumeroHeadersRichiesta()).isEqualTo(2);
        assertThat(dto.getContentTypeRisposta()).isEqualTo("application/soap+xml");
        assertThat(dto.getDimensioneRisposta()).isEqualTo(11L);
        assertThat(dto.getNumeroHeadersRisposta()).isEqualTo(1);

        assertThat(dto.getLinks().getSelf().getHref()).isEqualTo("/eventi/7");
        assertThat(dto.getLinks().getRichiesta().getHref()).isEqualTo("/eventi/7/richiesta");
        assertThat(dto.getLinks().getRisposta().getHref()).isEqualTo("/eventi/7/risposta");
        assertThat(dto.getLinks().getDominio().getHref()).isEqualTo("/domini/12345678901");
        assertThat(dto.getLinks().getPendenza().getHref()).isEqualTo("/pendenze/A2A1/PEND1");
    }

    @Test
    void toDetail_senzaPayloadNeDominioNePendenza_linkAssenti() {
        Evento evento = new Evento()
                .id(9L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("batch")
                .esito(EsitoEvento.OK);

        it.govpay.console.model.Evento dto = mapper.toDetail(evento, null);

        assertThat(dto.getContentTypeRichiesta()).isNull();
        assertThat(dto.getDimensioneRichiesta()).isNull();
        assertThat(dto.getNumeroHeadersRichiesta()).isEqualTo(0);
        assertThat(dto.getLinks().getSelf().getHref()).isEqualTo("/eventi/9");
        assertThat(dto.getLinks().getRichiesta()).isNull();
        assertThat(dto.getLinks().getRisposta()).isNull();
        assertThat(dto.getLinks().getDominio()).isNull();
        assertThat(dto.getLinks().getPendenza()).isNull();
    }

    @Test
    void toDetail_headerSenzaPayload_richiestaComunqueRegistrata() {
        // evento storico: header registrati ma nessun payload (V1 non lo salvava sempre)
        Evento evento = new Evento()
                .id(11L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.API_ENTE)
                .categoriaEvento(CategoriaEvento.INTERFACCIA)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("getPendenza")
                .esito(EsitoEvento.OK)
                .parametriRichiesta(new DettaglioRichiesta()
                        .headers(List.of(new Header().nome("X-Request-Id").valore("abc"))));

        it.govpay.console.model.Evento dto = mapper.toDetail(evento, null);

        assertThat(dto.getDimensioneRichiesta()).isNull();
        assertThat(dto.getNumeroHeadersRichiesta()).isEqualTo(1);
        assertThat(dto.getLinks().getRichiesta().getHref()).isEqualTo("/eventi/11/richiesta");
    }

    @Test
    void toDetail_conFrCorrelato_popolaLinkFlusso() {
        Evento evento = new Evento()
                .id(13L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("acquisizioneFR")
                .esito(EsitoEvento.OK)
                .idFr(99L);

        Fr fr = new Fr();
        fr.setCodDominio("12345678901");
        fr.setCodFlusso("FLUSSO-1");
        fr.setCodPsp("PSP-1");
        fr.setRevisione(2L);

        it.govpay.console.model.Evento dto = mapper.toDetail(evento, fr);

        assertThat(dto.getLinks().getFlusso().getHref()).isEqualTo("/flussi-rendicontazione/12345678901/FLUSSO-1/PSP-1/2");
    }

    @Test
    void toDetail_senzaFrCorrelato_linkFlussoAssente() {
        Evento evento = new Evento()
                .id(14L)
                .dataEvento(OffsetDateTime.now())
                .componente(ComponenteEvento.GOVPAY)
                .categoriaEvento(CategoriaEvento.INTERNO)
                .ruolo(RuoloEvento.SERVER)
                .tipoEvento("batch")
                .esito(EsitoEvento.OK);

        it.govpay.console.model.Evento dto = mapper.toDetail(evento, null);

        assertThat(dto.getLinks().getFlusso()).isNull();
    }
}
