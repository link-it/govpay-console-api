package it.govpay.console.pendenza;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.model.DebitorePosizione;
import it.govpay.console.model.OpzionePagamentoPosizione;
import it.govpay.console.model.PendenzaSummary;
import it.govpay.console.model.PosizioneDebitoria;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.service.PosizioneDebitoriaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    private final PosizioneDebitoriaService posizioneDebitoriaService;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final PendenzaAnagrafiche anagrafiche;
    private final PendenzaMapper pendenzaMapper;
    private final PosizioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public PosizioneService(PosizioneDebitoriaService posizioneDebitoriaService,
                            UnitaOperativaRepository unitaOperativaRepository,
                            PendenzaAnagrafiche anagrafiche,
                            PendenzaMapper pendenzaMapper,
                            PosizioneMapper mapper,
                            CurrentOperatorService currentOperatorService,
                            AuditService auditService) {
        this.posizioneDebitoriaService = posizioneDebitoriaService;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.anagrafiche = anagrafiche;
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

        it.govpay.pendenze.entity.PosizioneDebitoria posizione = posizioneDebitoriaService
                .trovaPerIdentificativo(idA2A, idPosizioneDebitoria)
                .orElseThrow(() -> nonTrovata(idA2A, idPosizioneDebitoria));

        // Il grafo si legge in due query, non per navigazione: i debitori (anche rimossi) con
        // la loro anagrafica, poi le opzioni con le pendenze. Le raccolte sono liste, e piu' di
        // una in un solo fetch non e' ammessa.
        List<PdDebitore> pdDebitori = entityManager.createQuery(
                        "select d from PdDebitore d join fetch d.debitore "
                                + "where d.posizioneDebitoria.id = :id order by d.posizione", PdDebitore.class)
                .setParameter("id", posizione.getId()).getResultList();
        List<OpzionePagamento> opzioni = entityManager.createQuery(
                        "select distinct o from OpzionePagamento o left join fetch o.pendenze "
                                + "where o.pdDebitore.posizioneDebitoria.id = :id order by o.id", OpzionePagamento.class)
                .setParameter("id", posizione.getId()).getResultList();

        List<Pendenza> tutte = opzioni.stream().flatMap(o -> o.getPendenze().stream()).toList();
        PendenzaAnagrafiche.Pagina pagina = anagrafiche.risolviPagina(tutte);

        Supplier<Set<Long>> dominiConUoEc = memoizzato(
                () -> unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili()));
        Map<Long, Boolean> visibili = new HashMap<>();
        for (Pendenza p : tutte) {
            Long idTipoVersamento = pagina.contesto(p).tipoVersamento() != null
                    ? pagina.contesto(p).tipoVersamento().getId() : null;
            visibili.put(p.getId(), VersamentoVisibilita.isVisibile(posizione.getIdDominio(),
                    posizione.getIdUnitaOperativa(), idTipoVersamento, dominiConUoEc, operatore));
        }
        if (!visibili.containsValue(true)) {
            log.debug("getPosizioneDebitoria ACL nega l'accesso (404 anti-leak) idA2A={} idPosizioneDebitoria={}",
                    idA2A, idPosizioneDebitoria);
            throw nonTrovata(idA2A, idPosizioneDebitoria);
        }

        Map<Long, List<OpzionePagamentoPosizione>> opzioniPerDebitore = new LinkedHashMap<>();
        for (OpzionePagamento o : opzioni) {
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
        List<DebitorePosizione> debitori = pdDebitori.stream()
                .map(d -> mapper.toDebitore(d, opzioniPerDebitore.getOrDefault(d.getId(), List.of())))
                .toList();

        PosizioneDebitoria risposta = mapper.toPosizione(idA2A, idPosizioneDebitoria, posizione, pagina, debitori);

        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", idA2A);
        dettaglio.put("idPosizioneDebitoria", idPosizioneDebitoria);
        dettaglio.put("identificativiDebitori", pdDebitori.stream()
                .map(d -> d.getDebitore().getIdentificativo()).toList());
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, posizione.getId(), dettaglio, operatore, request);
        return risposta;
    }

    private static Supplier<Set<Long>> memoizzato(Supplier<Set<Long>> origine) {
        Set<Long>[] valore = new Set[1];
        return () -> {
            if (valore[0] == null) {
                valore[0] = origine.get();
            }
            return valore[0];
        };
    }

    private static NotFoundException nonTrovata(String idA2A, String idPosizioneDebitoria) {
        return new NotFoundException("Posizione debitoria non trovata: idA2A=" + idA2A
                + ", idPosizioneDebitoria=" + idPosizioneDebitoria);
    }
}
