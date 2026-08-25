package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.eventi.EventoAcl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.model.TipoRiscossione;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.PagamentoRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.SingoloVersamentoRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;

/**
 * Dettaglio canonico {@code GET /riconciliazioni/{idDominio}/{id}} con
 * {@code riscossioni[]} sempre embedded. A differenza del dettaglio del
 * flusso di rendicontazione, qui non c'e' {@code Cache-Control} pubblico:
 * la riconciliazione e' mutabile ({@code IN_ELABORAZIONE} → {@code ACQUISITA}/
 * {@code ERRORE}).
 */
@Service
public class RiconciliazioneDetailService {

    private static final Set<TipoRiscossione> DEFAULT_TIPI = Set.of(TipoRiscossione.ENTRATA, TipoRiscossione.MBT);

    private final IncassoRepository incassoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final DominioRepository dominioRepository;
    private final RptRepository rptRepository;
    private final SingoloVersamentoRepository singoloVersamentoRepository;
    private final RiconciliazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final EventoAcl eventoAcl;
    private final AclAuthorizer aclAuthorizer;

    public RiconciliazioneDetailService(IncassoRepository incassoRepository,
                                        PagamentoRepository pagamentoRepository,
                                        DominioRepository dominioRepository,
                                        RptRepository rptRepository,
                                        SingoloVersamentoRepository singoloVersamentoRepository,
                                        RiconciliazioneMapper mapper,
                                        CurrentOperatorService currentOperatorService,
                                        EventoAcl eventoAcl,
                                        AclAuthorizer aclAuthorizer) {
        this.incassoRepository = incassoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.dominioRepository = dominioRepository;
        this.rptRepository = rptRepository;
        this.singoloVersamentoRepository = singoloVersamentoRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.eventoAcl = eventoAcl;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Riconciliazione> get(String idDominio, String id, List<TipoRiscossione> tipoRiscossione) {
        aclAuthorizer.requireLettura(AclServizio.RENDICONTAZIONI_E_INCASSI);
        Incasso incasso = loadVisibile(idDominio, id);

        Dominio dominio = dominioRepository.findByCodDominio(incasso.getCodDominio()).orElse(null);
        Map<String, Dominio> domini = dominio != null ? Map.of(dominio.getCodDominio(), dominio) : Map.of();

        Set<TipoRiscossione> tipiAmmessi = tipoRiscossione == null || tipoRiscossione.isEmpty()
                ? DEFAULT_TIPI
                : Set.copyOf(tipoRiscossione);
        List<Pagamento> riscossioni = pagamentoRepository.findByIdIncassoOrderByDataPagamentoAsc(incasso.getId())
                .stream()
                .filter(p -> p.getTipo() != null && tipiAmmessi.contains(TipoRiscossione.valueOf(p.getTipo())))
                .toList();

        Map<Long, Rpt> rptById = loadRpt(riscossioni);
        Map<Long, SingoloVersamento> singoliVersamentiById = loadSingoliVersamenti(riscossioni);

        Riconciliazione dto = mapper.toDetail(incasso, domini, riscossioni, rptById, singoliVersamentiById);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dto);
    }

    private Map<Long, Rpt> loadRpt(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getIdRpt).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return rptRepository.findAllById(ids).stream().collect(Collectors.toMap(Rpt::getId, r -> r));
    }

    private Map<Long, SingoloVersamento> loadSingoliVersamenti(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getIdSingoloVersamento).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return singoloVersamentoRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(SingoloVersamento::getId, sv -> sv));
    }

    /** Carica la riconciliazione per coppia applicando l'ACL (404 anti-leak, nessun audit sul 404). */
    private Incasso loadVisibile(String idDominio, String id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Incasso incasso = incassoRepository.findByCodDominioAndIdentificativo(idDominio, id)
                .orElseThrow(() -> new NotFoundException(notFoundMessage(idDominio, id)));
        if (!eventoAcl.isVisibile(incasso.getCodDominio(), operatore)) {
            throw new NotFoundException(notFoundMessage(idDominio, id));
        }
        return incasso;
    }

    private static String notFoundMessage(String idDominio, String id) {
        return "Riconciliazione non trovata: idDominio=" + idDominio + ", id=" + id;
    }
}
