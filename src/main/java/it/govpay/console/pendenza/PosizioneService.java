package it.govpay.console.pendenza;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.DebitorePosizione;
import it.govpay.console.model.OpzionePagamentoPosizione;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.model.PosizioneDebitoria;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.Pendenza;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servizio dell'endpoint {@code GET /posizioni-debitorie/{idA2A}/{idPosizioneDebitoria}}.
 *
 * <p>La posizione si vede se l'operatore vede il suo dominio e la sua unita' operativa e
 * almeno una delle sue pendenze: le pendenze di tipi non visibili sono omesse, i debitori
 * restano tutti. Ogni 200 scrive un audit, perche' espone l'anagrafica di tutti i debitori.
 */
@Service
public class PosizioneService {

    public static final String AZIONE_AUDIT_VISUALIZZA = "POSIZIONE_VISUALIZZA_DEBITORI";

    private static final Logger log = LoggerFactory.getLogger(PosizioneService.class);

    private final PosizioneGrafo grafo;
    private final PendenzaMapper pendenzaMapper;
    private final PosizioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public PosizioneService(PosizioneGrafo grafo,
                            PendenzaMapper pendenzaMapper,
                            PosizioneMapper mapper,
                            CurrentOperatorService currentOperatorService,
                            AuditService auditService) {
        this.grafo = grafo;
        this.pendenzaMapper = pendenzaMapper;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PosizioneDebitoria get(String idA2A, String idPosizioneDebitoria, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getPosizioneDebitoria idA2A={} idPosizioneDebitoria={} operatore={}",
                idA2A, idPosizioneDebitoria, operatore.principal());

        PosizioneGrafo.Caricato caricato = grafo.carica(idA2A, idPosizioneDebitoria);
        it.govpay.pendenze.entity.PosizioneDebitoria posizione = caricato.posizione();
        PendenzaAnagrafiche.Pagina pagina = caricato.pagina();

        Map<Long, Boolean> visibili = grafo.visibilita(caricato, operatore);
        if (!visibili.containsValue(true)) {
            throw grafo.nonTrovata(idA2A, idPosizioneDebitoria);
        }

        Map<Long, List<OpzionePagamentoPosizione>> opzioniPerDebitore = new LinkedHashMap<>();
        for (OpzionePagamento o : caricato.opzioni()) {
            List<PendenzaSummary> pendenze = o.getPendenze().stream()
                    .filter(p -> visibili.get(p.getId()))
                    .sorted(Comparator.comparingInt(Pendenza::getNumeroRata))
                    .map(p -> pendenzaMapper.toSummary(p, idA2A, pagina.contesto(p)))
                    .toList();
            if (!pendenze.isEmpty()) {
                opzioniPerDebitore.computeIfAbsent(o.getPdDebitore().getId(), k -> new ArrayList<>())
                        .add(mapper.toOpzione(o, pendenze));
            }
        }
        List<DebitorePosizione> debitori = caricato.pdDebitori().stream()
                .map(d -> mapper.toDebitore(d, opzioniPerDebitore.getOrDefault(d.getId(), List.of())))
                .toList();

        PosizioneDebitoria risposta = mapper.toPosizione(idA2A, idPosizioneDebitoria, posizione, pagina, debitori);

        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", idA2A);
        dettaglio.put("idPosizioneDebitoria", idPosizioneDebitoria);
        dettaglio.put("identificativiDebitori", caricato.pdDebitori().stream()
                .map(d -> d.getDebitore().getIdentificativo()).toList());
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, posizione.getId(), dettaglio, operatore, request);
        return risposta;
    }
}
