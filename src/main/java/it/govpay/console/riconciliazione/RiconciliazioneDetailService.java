package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Incasso;
import it.govpay.pendenze.entity.Pagamento;
import it.govpay.console.riconciliazione.RiscossioniResolver.RiferimentoPendenza;
import it.govpay.console.eventi.EventoAcl;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.model.TipoRiscossione;
import it.govpay.console.repository.ConsoleDominioRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.ConsolePagamentoRepository;
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
    private final ConsolePagamentoRepository pagamentoRepository;
    private final ConsoleDominioRepository dominioRepository;
    private final RiscossioniResolver riscossioniResolver;
    private final RiconciliazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final EventoAcl eventoAcl;
    private final AclAuthorizer aclAuthorizer;

    public RiconciliazioneDetailService(IncassoRepository incassoRepository,
                                        ConsolePagamentoRepository pagamentoRepository,
                                        ConsoleDominioRepository dominioRepository,
                                        RiscossioniResolver riscossioniResolver,
                                        RiconciliazioneMapper mapper,
                                        CurrentOperatorService currentOperatorService,
                                        EventoAcl eventoAcl,
                                        AclAuthorizer aclAuthorizer) {
        this.incassoRepository = incassoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.dominioRepository = dominioRepository;
        this.riscossioniResolver = riscossioniResolver;
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
                .filter(p -> p.getTipo() != null && tipiAmmessi.contains(TipoRiscossione.valueOf(p.getTipo().name())))
                .toList();

        Map<Long, String> iurRptById = riscossioniResolver.loadIurRpt(riscossioni);
        Map<Long, RiferimentoPendenza> singoliVersamentiById = riscossioniResolver.loadPendenze(riscossioni);

        Riconciliazione dto = mapper.toDetail(incasso, domini, riscossioni, iurRptById, singoliVersamentiById);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dto);
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
