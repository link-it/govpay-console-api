package it.govpay.console.connettoreintegrazione;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.connettore.ConnettoreStore;
import it.govpay.console.entity.Applicazione;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione;
import it.govpay.console.repository.ApplicazioneRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione.TipoAutenticazioneEnum;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import it.govpay.console.web.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Gestisce l'unico connettore del servizio di integrazione di un'applicazione,
 * memorizzato come proprieta' EAV su {@code connettori} sotto il codice
 * {@code <idA2A>_INTEGRAZIONE} (referenziato da {@code applicazioni.cod_connettore_integrazione}).
 */
@Service
public class ConnettoreIntegrazioneService {

    public static final String AZIONE_AUDIT_MODIFICA = "CONNETTORE_INTEGRAZIONE_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "CONNETTORE_INTEGRAZIONE_CREDENZIALI";

    private static final String COD_CONNETTORE_SUFFIX = "_INTEGRAZIONE";

    private final ApplicazioneRepository applicazioneRepository;
    private final ConnettoreStore store;
    private final ConnettoreIntegrazioneMapper mapper;
    private final ObjectMapper objectMapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public ConnettoreIntegrazioneService(ApplicazioneRepository applicazioneRepository,
                                         ConnettoreStore store,
                                         ConnettoreIntegrazioneMapper mapper,
                                         ObjectMapper objectMapper,
                                         CurrentOperatorService currentOperatorService,
                                         AuditService auditService) {
        this.applicazioneRepository = applicazioneRepository;
        this.store = store;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ConnettoreIntegrazioneApplicazione> get(String idA2A) {
        Applicazione app = load(idA2A);
        ConnettoreIntegrazioneApplicazione dto = mapper.toDto(readConfig(app.getCodConnettoreIntegrazione()));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public ResponseEntity<ConnettoreIntegrazioneApplicazione> replace(String idA2A,
                                                                      ConnettoreIntegrazioneApplicazione body,
                                                                      String ifMatch,
                                                                      HttpServletRequest request) {
        Applicazione app = load(idA2A);
        checkIfMatch(ifMatch, mapper.toDto(readConfig(app.getCodConnettoreIntegrazione())));
        validate(body);

        String cod = ensureCodConnettore(app);
        store.upsert(cod, mapper.toConfigMap(body), ConnettoreIntegrazioneMapper.CONFIG_KEYS);

        audit(AZIONE_AUDIT_MODIFICA, app, request);

        ConnettoreIntegrazioneApplicazione updated = mapper.toDto(store.read(cod));
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(String idA2A, ConnettoreCredenziali credenziali,
                                               HttpServletRequest request) {
        Applicazione app = load(idA2A);
        String cod = ensureCodConnettore(app);
        store.upsert(cod, mapper.toCredenzialiMap(credenziali), ConnettoreIntegrazioneMapper.CREDENTIAL_KEYS);

        audit(AZIONE_AUDIT_CREDENZIALI, app, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Con {@code tipoAutenticazione=SSL} il tipo SSL (CLIENT/SERVER) e'
     * obbligatorio: in V1 il connettore SSL ha sempre {@code TIPOSSL}
     * valorizzato, lasciarlo assente produrrebbe una configurazione zoppa.
     */
    private static void validate(ConnettoreIntegrazioneApplicazione body) {
        if (body.getTipoAutenticazione() == TipoAutenticazioneEnum.SSL && body.getSslTipo() == null) {
            throw new UnprocessableEntityException(
                    "Con 'tipoAutenticazione'=SSL il campo 'sslTipo' (CLIENT o SERVER) e' obbligatorio.");
        }
    }

    private Map<String, String> readConfig(String codConnettore) {
        return codConnettore == null ? new HashMap<>() : store.read(codConnettore);
    }

    private String ensureCodConnettore(Applicazione app) {
        String cod = app.getCodConnettoreIntegrazione();
        if (cod != null) {
            return cod;
        }
        String generated = app.getCodApplicazione() + COD_CONNETTORE_SUFFIX;
        app.setCodConnettoreIntegrazione(generated);
        applicazioneRepository.save(app);
        return generated;
    }

    private Applicazione load(String idA2A) {
        return applicazioneRepository.findByCodApplicazione(idA2A)
                .orElseThrow(() -> new NotFoundException("Applicazione non trovata: " + idA2A));
    }

    private void checkIfMatch(String ifMatch, Object dto) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException(
                    "Header 'If-Match' obbligatorio per le operazioni di modifica.");
        }
        if (!RepresentationEtag.matches(ifMatch, dto, objectMapper)) {
            throw new IfMatchMismatchException(
                    "L'header 'If-Match' non corrisponde alla configurazione corrente del connettore.");
        }
    }

    private void audit(String azione, Applicazione app, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", app.getCodApplicazione());
        auditService.registra(azione, app.getId(), dettaglio, operatore, request);
    }
}
