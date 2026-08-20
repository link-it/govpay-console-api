package it.govpay.console.eventi;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.EventoHeader;
import it.govpay.console.model.EventoRichiesta;
import it.govpay.console.model.EventoRisposta;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import it.govpay.gde.client.beans.DettaglioRichiesta;
import it.govpay.gde.client.beans.DettaglioRisposta;
import it.govpay.gde.client.beans.Header;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Sub-resource sensibili {@code GET /eventi/{id}/richiesta} e
 * {@code .../risposta}: unica fonte del payload/header registrati da GDE per
 * un evento, con redazione degli header sensibili di default e audit GDPR
 * dedicato. Nessun gate ACL aggiuntivo per {@code ?unmask=true} (deciso
 * esplicitamente: V1 non aveva alcun livello elevato per il giornale eventi,
 * la salvaguardia e' l'audit distinto {@value #AZIONE_AUDIT_CREDENZIALI},
 * severity HIGH nel dettaglio audit).
 */
@Service
public class EventoSubResourceService {

    public static final String AZIONE_AUDIT_RICHIESTA = "EVENTO_RICHIESTA_VISUALIZZA";
    public static final String AZIONE_AUDIT_RISPOSTA = "EVENTO_RISPOSTA_VISUALIZZA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "EVENTO_CREDENZIALI_VISUALIZZA";

    private static final String VALORE_REDATTO = "***REDACTED***";

    private final EventoGdeClient client;
    private final EventoAcl eventoAcl;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final Set<String> headerSensibili;

    public EventoSubResourceService(EventoGdeClient client, EventoAcl eventoAcl,
            CurrentOperatorService currentOperatorService, AuditService auditService,
            @Value("${govpay.giornale-eventi.header-sensibili:"
                    + "Authorization,Proxy-Authorization,Cookie,Set-Cookie,X-Api-Key,X-Auth-Token,X-Csrf-Token}")
            String headerSensibiliCsv) {
        this.client = client;
        this.eventoAcl = eventoAcl;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.headerSensibili = Arrays.stream(headerSensibiliCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public EventoRichiesta getRichiesta(Long id, boolean unmask, HttpServletRequest request) {
        it.govpay.gde.client.beans.Evento evento = fetchVisibile(id);
        DettaglioRichiesta d = evento.getParametriRichiesta();
        List<Header> headers = d != null ? d.getHeaders() : null;
        String payload = d != null ? d.getPayload() : null;
        if (nonRegistrato(headers, payload)) {
            throw new NotFoundException("Richiesta non registrata per l'evento: " + id);
        }

        EventoRichiesta dto = new EventoRichiesta();
        dto.setHeaders(mapHeaders(headers, unmask));
        dto.setPayload(decodePayload(payload));

        audit(AZIONE_AUDIT_RICHIESTA, id, unmask, request);
        return dto;
    }

    @Transactional(readOnly = true)
    public EventoRisposta getRisposta(Long id, boolean unmask, HttpServletRequest request) {
        it.govpay.gde.client.beans.Evento evento = fetchVisibile(id);
        DettaglioRisposta d = evento.getParametriRisposta();
        List<Header> headers = d != null ? d.getHeaders() : null;
        String payload = d != null ? d.getPayload() : null;
        if (nonRegistrato(headers, payload)) {
            throw new NotFoundException("Risposta non registrata per l'evento: " + id);
        }

        EventoRisposta dto = new EventoRisposta();
        dto.setHeaders(mapHeaders(headers, unmask));
        dto.setPayload(decodePayload(payload));

        audit(AZIONE_AUDIT_RISPOSTA, id, unmask, request);
        return dto;
    }

    private it.govpay.gde.client.beans.Evento fetchVisibile(Long id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        it.govpay.gde.client.beans.Evento evento = client.getEventoById(id);
        if (!eventoAcl.isVisibile(evento.getIdDominio(), operatore)) {
            throw new NotFoundException("Evento non trovato: " + id);
        }
        return evento;
    }

    private static boolean nonRegistrato(List<Header> headers, String payload) {
        return (headers == null || headers.isEmpty()) && (payload == null || payload.isBlank());
    }

    private List<EventoHeader> mapHeaders(List<Header> headers, boolean unmask) {
        if (headers == null) {
            return List.of();
        }
        return headers.stream().map(h -> mapHeader(h, unmask)).toList();
    }

    private EventoHeader mapHeader(Header header, boolean unmask) {
        boolean sensibile = header.getNome() != null
                && headerSensibili.contains(header.getNome().toLowerCase(java.util.Locale.ROOT));
        EventoHeader dto = new EventoHeader();
        dto.setNome(header.getNome());
        if (sensibile && !unmask) {
            dto.setValore(VALORE_REDATTO);
            dto.setRedatto(true);
        } else {
            dto.setValore(header.getValore());
            dto.setRedatto(false);
        }
        return dto;
    }

    private static String decodePayload(String payloadBase64) {
        if (payloadBase64 == null || payloadBase64.isBlank()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
    }

    private void audit(String azione, Long id, boolean unmask, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idEvento", id);
        dettaglio.put("unmask", unmask);
        auditService.registra(azione, id, dettaglio, operatore, request);

        if (unmask) {
            Map<String, Object> dettaglioCredenziali = new HashMap<>();
            dettaglioCredenziali.put("idEvento", id);
            dettaglioCredenziali.put("azioneCorrelata", azione);
            dettaglioCredenziali.put("severity", "HIGH");
            auditService.registra(AZIONE_AUDIT_CREDENZIALI, id, dettaglioCredenziali, operatore, request);
        }
    }
}
