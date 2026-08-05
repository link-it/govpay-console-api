package it.govpay.console.eventi;

import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Fr;
import it.govpay.console.model.CategoriaEvento;
import it.govpay.console.model.ComponenteEvento;
import it.govpay.console.model.DatiPagoPA;
import it.govpay.console.model.EsitoEvento;
import it.govpay.console.model.Evento;
import it.govpay.console.model.EventoLinks;
import it.govpay.console.model.EventoSummary;
import it.govpay.console.model.Link;
import it.govpay.console.model.RuoloEvento;

/**
 * Traduce {@code it.govpay.gde.client.beans.Evento} (client generato in
 * govpay-common dallo schema di GDE) in {@link EventoSummary} (schema V2 di
 * console-api). Gli enum sono distinti per pacchetto/generatore ma condividono
 * gli stessi nomi di costante: mappatura tramite {@code name()}, senza switch
 * da mantenere in sincronia con GDE a mano.
 */
@Component
public class EventoMapper {

    public EventoSummary toSummary(it.govpay.gde.client.beans.Evento evento) {
        EventoSummary dto = new EventoSummary();
        dto.setId(evento.getId());
        dto.setDataEvento(evento.getDataEvento());
        dto.setDurataEventoMs(evento.getDurataEvento());
        dto.setComponente(componente(evento.getComponente()));
        dto.setCategoriaEvento(categoria(evento.getCategoriaEvento()));
        dto.setRuolo(ruolo(evento.getRuolo()));
        dto.setTipoEvento(evento.getTipoEvento());
        dto.setSottotipoEvento(evento.getSottotipoEvento());
        dto.setEsito(esito(evento.getEsito()));
        dto.setSottotipoEsito(evento.getSottotipoEsito());
        dto.setDettaglioEsito(evento.getDettaglioEsito());
        dto.setSeverita(evento.getSeverita());
        dto.setIdDominio(evento.getIdDominio());
        dto.setIuv(evento.getIuv());
        dto.setCcp(evento.getCcp());
        dto.setIdA2A(evento.getIdA2A());
        dto.setIdPendenza(evento.getIdPendenza());
        dto.setIdPagamento(evento.getIdPagamento());
        dto.setTransactionId(evento.getTransactionId());
        dto.setClusterId(evento.getClusterId());
        dto.setDatiPagoPA(datiPagoPA(evento.getDatiPagoPA()));
        return dto;
    }

    /**
     * Dettaglio metadata-only: gli stessi campi della summary + info sui
     * payload (content-type, dimensione, numero header) derivate da
     * {@code parametriRichiesta}/{@code parametriRisposta} di GDE, senza
     * includerli. GDE li ritorna gia' inline e non redatti: qui si estrae
     * solo la "forma" (quanto pesano, quanti header, che content-type), mai
     * headers/payload stessi — quelli sono responsabilita' dei sub-resource
     * dedicati (con redazione e audit).
     *
     * @param frCorrelato il flusso di rendicontazione risolto da {@code evento.getIdFr()}
     *                    (gia' caricato dal service, se presente), per {@code _links.flusso}
     */
    public Evento toDetail(it.govpay.gde.client.beans.Evento evento, Fr frCorrelato) {
        Evento dto = new Evento();
        dto.setId(evento.getId());
        dto.setDataEvento(evento.getDataEvento());
        dto.setDurataEventoMs(evento.getDurataEvento());
        dto.setComponente(componente(evento.getComponente()));
        dto.setCategoriaEvento(categoria(evento.getCategoriaEvento()));
        dto.setRuolo(ruolo(evento.getRuolo()));
        dto.setTipoEvento(evento.getTipoEvento());
        dto.setSottotipoEvento(evento.getSottotipoEvento());
        dto.setEsito(esito(evento.getEsito()));
        dto.setSottotipoEsito(evento.getSottotipoEsito());
        dto.setDettaglioEsito(evento.getDettaglioEsito());
        dto.setSeverita(evento.getSeverita());
        dto.setIdDominio(evento.getIdDominio());
        dto.setIuv(evento.getIuv());
        dto.setCcp(evento.getCcp());
        dto.setIdA2A(evento.getIdA2A());
        dto.setIdPendenza(evento.getIdPendenza());
        dto.setIdPagamento(evento.getIdPagamento());
        dto.setTransactionId(evento.getTransactionId());
        dto.setClusterId(evento.getClusterId());
        dto.setDatiPagoPA(datiPagoPA(evento.getDatiPagoPA()));

        PayloadMeta richiesta = payloadMeta(evento.getParametriRichiesta());
        PayloadMeta risposta = payloadMeta(evento.getParametriRisposta());

        dto.setContentTypeRichiesta(richiesta.contentType());
        dto.setDimensioneRichiesta(richiesta.dimensione());
        dto.setNumeroHeadersRichiesta(richiesta.numeroHeaders());
        dto.setContentTypeRisposta(risposta.contentType());
        dto.setDimensioneRisposta(risposta.dimensione());
        dto.setNumeroHeadersRisposta(risposta.numeroHeaders());

        dto.setLinks(links(evento.getId(), evento.getIdDominio(), evento.getIdA2A(), evento.getIdPendenza(),
                richiesta, risposta, frCorrelato));
        return dto;
    }

    private static PayloadMeta payloadMeta(it.govpay.gde.client.beans.DettaglioRichiesta d) {
        return d == null ? PayloadMeta.ASSENTE : payloadMeta(d.getHeaders(), d.getPayload());
    }

    private static PayloadMeta payloadMeta(it.govpay.gde.client.beans.DettaglioRisposta d) {
        return d == null ? PayloadMeta.ASSENTE : payloadMeta(d.getHeaders(), d.getPayload());
    }

    private static PayloadMeta payloadMeta(List<it.govpay.gde.client.beans.Header> headers, String payloadBase64) {
        int numeroHeaders = headers == null ? 0 : headers.size();
        String contentType = headers == null ? null : headers.stream()
                .filter(h -> "Content-Type".equalsIgnoreCase(h.getNome()))
                .map(it.govpay.gde.client.beans.Header::getValore)
                .findFirst().orElse(null);
        return new PayloadMeta(contentType, base64DecodedLength(payloadBase64), numeroHeaders);
    }

    /**
     * Dimensione decodificata di una stringa base64 senza materializzare i
     * byte: {@code (lunghezza/4)*3 - padding}. Evita di allocare/decodificare
     * un payload potenzialmente pesante (RPT/RT) solo per contarne i byte —
     * GDE lo ritorna comunque inline nella risposta, ma almeno qui non lo
     * decodifichiamo per buttarlo via subito dopo.
     */
    private static Long base64DecodedLength(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        int len = base64.length();
        int padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
        return (len / 4L) * 3 - padding;
    }

    private static EventoLinks links(Long id, String idDominio, String idA2A, String idPendenza,
            PayloadMeta richiesta, PayloadMeta risposta, Fr frCorrelato) {
        EventoLinks links = new EventoLinks();
        links.setSelf(new Link("/eventi/" + id));
        if (richiesta.registrato()) {
            links.setRichiesta(new Link("/eventi/" + id + "/richiesta"));
        }
        if (risposta.registrato()) {
            links.setRisposta(new Link("/eventi/" + id + "/risposta"));
        }
        if (idDominio != null) {
            links.setDominio(new Link("/domini/" + idDominio));
        }
        if (idA2A != null && idPendenza != null) {
            links.setPendenza(new Link("/pendenze/" + idA2A + "/" + idPendenza));
        }
        if (frCorrelato != null) {
            links.setFlusso(new Link("/flussi-rendicontazione/" + frCorrelato.getCodDominio()
                    + "/" + frCorrelato.getCodFlusso() + "/" + frCorrelato.getCodPsp()
                    + "/" + frCorrelato.getRevisione()));
        }
        return links;
    }

    /** {@code numeroHeaders} e' sempre 0 (mai null) anche quando il payload non e' registrato. */
    private record PayloadMeta(String contentType, Long dimensione, Integer numeroHeaders) {
        static final PayloadMeta ASSENTE = new PayloadMeta(null, null, 0);

        boolean registrato() {
            return (dimensione != null && dimensione > 0) || (numeroHeaders != null && numeroHeaders > 0);
        }
    }

    private static ComponenteEvento componente(it.govpay.gde.client.beans.ComponenteEvento v) {
        return v == null ? null : ComponenteEvento.valueOf(v.name());
    }

    private static CategoriaEvento categoria(it.govpay.gde.client.beans.CategoriaEvento v) {
        return v == null ? null : CategoriaEvento.valueOf(v.name());
    }

    private static RuoloEvento ruolo(it.govpay.gde.client.beans.RuoloEvento v) {
        return v == null ? null : RuoloEvento.valueOf(v.name());
    }

    private static EsitoEvento esito(it.govpay.gde.client.beans.EsitoEvento v) {
        return v == null ? null : EsitoEvento.valueOf(v.name());
    }

    private static DatiPagoPA datiPagoPA(it.govpay.gde.client.beans.DatiPagoPA v) {
        if (v == null) {
            return null;
        }
        DatiPagoPA dto = new DatiPagoPA();
        dto.setIdPsp(v.getIdPsp());
        dto.setIdCanale(v.getIdCanale());
        dto.setIdIntermediarioPsp(v.getIdIntermediarioPsp());
        dto.setTipoVersamento(v.getTipoVersamento());
        dto.setModelloPagamento(v.getModelloPagamento());
        dto.setIdDominio(v.getIdDominio());
        dto.setIdIntermediario(v.getIdIntermediario());
        dto.setIdStazione(v.getIdStazione());
        dto.setIdRiconciliazione(v.getIdRiconciliazione());
        dto.setSct(v.getSct());
        dto.setIdFlusso(v.getIdFlusso());
        dto.setIdTracciato(v.getIdTracciato());
        dto.setIdentificativoErogatore(v.getIdentificativoErogatore());
        dto.setIdentificativoFruitore(v.getIdentificativoFruitore());
        return dto;
    }
}
