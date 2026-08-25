package it.govpay.console.riconciliazione;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.NuovaRiconciliazione;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Entry point di {@code PUT /riconciliazioni/{idDominio}/{id}}: NON è
 * {@code @Transactional} di suo — delega a {@link RiconciliazioneUpsertService}
 * (bean separato, cross-proxy) e gestisce la concorrenza: due PUT paralleli
 * sulla stessa coppia possono violare {@code unique_incassi_1} in insert; qui
 * si intercetta e si ritenta, stavolta la riga esiste già e l'upsert segue
 * naturalmente il ramo idempotente/riaccodante.
 *
 * <p>L'audit vive qui (non in {@code RiconciliazioneUpsertService}) perché è
 * l'unico livello che osserva sia l'esito positivo (200/202, distinti
 * esplicitamente) sia le eccezioni di pre-flight (400/403/404/409): un
 * audit registrato solo a valle del successo lascerebbe silenziosi tutti i
 * tentativi respinti, che sono comunque operazioni contabili rilevanti.
 */
@Service
public class RiconciliazioneWriteService {

    private static final Logger log = LoggerFactory.getLogger(RiconciliazioneWriteService.class);

    private static final String AZIONE_AUDIT_REGISTRA = "RICONCILIAZIONE_REGISTRA";

    private final RiconciliazioneUpsertService upsertService;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;

    public RiconciliazioneWriteService(RiconciliazioneUpsertService upsertService,
                                       AuditService auditService,
                                       CurrentOperatorService currentOperatorService) {
        this.upsertService = upsertService;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
    }

    public ResponseEntity<Riconciliazione> put(String idDominio, String id, NuovaRiconciliazione body,
                                               HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();

        UpsertResult result;
        try {
            result = eseguiConRetrySuRace(idDominio, id, body);
        } catch (RuntimeException e) {
            registraAudit(idDominio, id, body, null, esitoDa(e), operatore, request);
            throw e;
        }

        registraAudit(idDominio, id, body, result.idIncasso(), result.accettata() ? "202" : "200", operatore, request);

        if (!result.accettata()) {
            return ResponseEntity.ok(result.body());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
        return ResponseEntity.accepted().location(location).body(result.body());
    }

    private UpsertResult eseguiConRetrySuRace(String idDominio, String id, NuovaRiconciliazione body) {
        try {
            return upsertService.upsert(idDominio, id, body);
        } catch (DataIntegrityViolationException e) {
            log.debug("Insert concorrente su ({}, {}): ri-eseguo l'upsert, ora idempotente.", idDominio, id);
            return upsertService.upsert(idDominio, id, body);
        }
    }

    private void registraAudit(String idDominio, String id, NuovaRiconciliazione body, Long idIncasso, String esito,
                               OperatoreCorrente operatore, HttpServletRequest request) {
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", idDominio);
        dettaglio.put("id", id);
        dettaglio.put("idFlusso", body.getIdFlusso());
        dettaglio.put("causale", body.getCausale());
        dettaglio.put("importo", body.getImporto());
        dettaglio.put("esito", esito);
        auditService.registra(AZIONE_AUDIT_REGISTRA, idIncasso != null ? idIncasso : 0L, dettaglio, operatore, request);
    }

    private static String esitoDa(RuntimeException e) {
        if (e instanceof BadRequestException) {
            return "400";
        }
        if (e instanceof AccessDeniedException) {
            return "403";
        }
        if (e instanceof NotFoundException) {
            return "404";
        }
        if (e instanceof ConflictException) {
            return "409";
        }
        return "500";
    }
}
