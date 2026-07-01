package it.govpay.console.connettoredominio;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.connettore.ConnettoreStore;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.JppaConfig;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.JppaConfigRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.IfMatchMismatchException;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PreconditionRequiredException;
import it.govpay.console.web.RepresentationEtag;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * CRUD dei connettori di notifica pagamenti del dominio. Riusa la persistenza
 * EAV ({@link ConnettoreStore}) dei connettori; il riferimento
 * {@code cod_connettore} vive sulla colonna {@code cod_connettore_*} del dominio,
 * tranne per Maggioli JPPA che usa la tabella {@code jppa_config}. Per Maggioli
 * il flag {@code abilitato} e' autoritativo su {@code jppa_config}, mentre
 * {@code data_ultima_rt} (stato del batch) non e' esposto ne' modificato.
 */
@Service
public class ConnettoreDominioService {

    public static final String AZIONE_AUDIT_MODIFICA = "CONNETTORE_DOMINIO_MODIFICA";
    public static final String AZIONE_AUDIT_CREDENZIALI = "CONNETTORE_DOMINIO_CREDENZIALI";

    private final DominioRepository dominioRepository;
    private final JppaConfigRepository jppaConfigRepository;
    private final ConnettoreStore store;
    private final ConnettoreDominioMapper mapper;
    private final ObjectMapper objectMapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public ConnettoreDominioService(DominioRepository dominioRepository,
                                    JppaConfigRepository jppaConfigRepository,
                                    ConnettoreStore store,
                                    ConnettoreDominioMapper mapper,
                                    ObjectMapper objectMapper,
                                    CurrentOperatorService currentOperatorService,
                                    AuditService auditService) {
        this.dominioRepository = dominioRepository;
        this.jppaConfigRepository = jppaConfigRepository;
        this.store = store;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public <T> ResponseEntity<T> get(String idDominio, ConnettoreDominioCanale canale, Class<T> dtoClass) {
        Dominio dominio = loadDominio(idDominio);
        T dto = toDto(dominio, canale, dtoClass);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(dto, objectMapper))
                .body(dto);
    }

    @Transactional
    public <T> ResponseEntity<T> replace(String idDominio, ConnettoreDominioCanale canale,
                                         T dto, String ifMatch, Class<T> dtoClass,
                                         HttpServletRequest request) {
        Dominio dominio = loadDominio(idDominio);
        checkIfMatch(ifMatch, toDto(dominio, canale, dtoClass));

        String cod = ensureCodConnettore(dominio, canale);
        Map<String, String> desired = mapper.toConfigMap(objectMapper.valueToTree(dto), canale);
        store.upsert(cod, desired, ConnettoreDominioProprietaKeys.CONFIG_KEYS);

        if (canale.isMaggioliJppa()) {
            upsertJppaConfig(dominio, cod, "true".equals(desired.get(ConnettoreDominioProprietaKeys.ABILITATO)));
        }

        audit(AZIONE_AUDIT_MODIFICA, dominio, canale, request);

        T updated = toDto(dominio, canale, dtoClass);
        return ResponseEntity.ok()
                .eTag(RepresentationEtag.of(updated, objectMapper))
                .body(updated);
    }

    @Transactional
    public ResponseEntity<Void> putCredenziali(String idDominio, ConnettoreDominioCanale canale,
                                               ConnettoreCredenziali credenziali, HttpServletRequest request) {
        Dominio dominio = loadDominio(idDominio);
        String cod = ensureCodConnettore(dominio, canale);

        Map<String, String> desired = mapper.toCredenzialiMap(objectMapper.valueToTree(credenziali));
        store.upsert(cod, desired, ConnettoreDominioProprietaKeys.CREDENTIAL_KEYS);

        audit(AZIONE_AUDIT_CREDENZIALI, dominio, canale, request);
        return ResponseEntity.noContent().build();
    }

    // --- lettura ---

    private <T> T toDto(Dominio dominio, ConnettoreDominioCanale canale, Class<T> dtoClass) {
        String cod = resolveCodConnettore(dominio, canale);
        Map<String, String> config = cod == null ? new HashMap<>() : store.read(cod);
        ObjectNode node = mapper.toDtoNode(config, canale);
        if (canale.isMaggioliJppa()) {
            node.put("abilitato", maggioliAbilitato(dominio));
        }
        return objectMapper.treeToValue(node, dtoClass);
    }

    private boolean maggioliAbilitato(Dominio dominio) {
        return jppaConfigRepository.findByCodDominio(dominio.getCodDominio())
                .map(JppaConfig::isAbilitato)
                .orElse(false);
    }

    // --- risoluzione cod_connettore ---

    private String resolveCodConnettore(Dominio dominio, ConnettoreDominioCanale canale) {
        if (canale.isMaggioliJppa()) {
            return jppaConfigRepository.findByCodDominio(dominio.getCodDominio())
                    .map(JppaConfig::getCodConnettore)
                    .orElse(null);
        }
        return canale.codConnettore(dominio);
    }

    private String ensureCodConnettore(Dominio dominio, ConnettoreDominioCanale canale) {
        String cod = resolveCodConnettore(dominio, canale);
        if (cod != null) {
            return cod;
        }
        String generated = canale.generaCodConnettore(dominio.getCodDominio());
        if (canale.isMaggioliJppa()) {
            upsertJppaConfig(dominio, generated, null);
        } else {
            canale.setCodConnettore(dominio, generated);
            dominioRepository.save(dominio);
        }
        return generated;
    }

    private void upsertJppaConfig(Dominio dominio, String cod, Boolean abilitato) {
        JppaConfig jppaConfig = jppaConfigRepository.findByCodDominio(dominio.getCodDominio())
                .orElseGet(() -> {
                    JppaConfig nuovo = new JppaConfig();
                    nuovo.setCodDominio(dominio.getCodDominio());
                    nuovo.setIdDominio(dominio.getId());
                    nuovo.setAbilitato(false);
                    return nuovo;
                });
        jppaConfig.setCodConnettore(cod);
        if (abilitato != null) {
            jppaConfig.setAbilitato(abilitato);
        }
        // data_ultima_rt (stato del batch) non viene mai toccato qui.
        jppaConfigRepository.save(jppaConfig);
    }

    // --- helpers ---

    private Dominio loadDominio(String idDominio) {
        return dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
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

    private void audit(String azione, Dominio dominio, ConnettoreDominioCanale canale,
                       HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", dominio.getCodDominio());
        dettaglio.put("canale", canale.name());
        auditService.registra(azione, dominio.getId(), dettaglio, operatore, request);
    }
}
