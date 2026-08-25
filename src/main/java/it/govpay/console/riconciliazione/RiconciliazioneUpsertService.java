package it.govpay.console.riconciliazione;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;
import it.govpay.console.config.OperazioniProperties;
import it.govpay.console.config.OperazioniProperties.OperazioneConfig;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Fr;
import it.govpay.console.entity.Incasso;
import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.NuovaRiconciliazione;
import it.govpay.console.model.Riconciliazione;
import it.govpay.console.operazioni.OperazioneBatchClient;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.IncassoRepository;
import it.govpay.console.repository.PagamentoRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Logica transazionale di {@code PUT /riconciliazioni/{idDominio}/{id}}:
 * pre-flight (§6 issue), upsert idempotente, hook di attivazione batch
 * post-commit. Separata da {@link RiconciliazioneWriteService} (non
 * transazionale) perché la gestione della race di concorrenza richiede che
 * il retry avvenga **dopo** un rollback reale — la self-invocation di
 * {@code @Transactional} nella stessa classe non lo garantirebbe (la
 * transazione non si aprirebbe/chiuderebbe passando dal proxy).
 *
 * <p>L'audit (esito incluso, anche sui 4xx) è registrato in
 * {@link RiconciliazioneWriteService}, l'unico livello che osserva sia il
 * successo sia le eccezioni di pre-flight sollevate qui.
 */
@Service
public class RiconciliazioneUpsertService {

    private static final Logger log = LoggerFactory.getLogger(RiconciliazioneUpsertService.class);

    /** Id del batch nel catalogo operazioni. Allineato a {@code OperazioniDAO.ELABORAZIONE_RICONCILIAZIONI} del core. */
    static final String ID_OPERAZIONE_TRIGGER = "ELABORAZIONE_RICONCILIAZIONI";

    private static final String STATO_ERRORE = "ERRORE";
    private static final String STATO_NUOVO = "NUOVO";
    private static final String STATO_FR_ACCETTATA = "ACCETTATA";

    private final IncassoRepository incassoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final DominioRepository dominioRepository;
    private final IbanAccreditoRepository ibanAccreditoRepository;
    private final FrRepository frRepository;
    private final RiscossioniResolver riscossioniResolver;
    private final RiconciliazioneMapper mapper;
    private final CurrentOperatorService currentOperatorService;
    private final AclAuthorizer aclAuthorizer;
    private final OperazioniProperties operazioniProperties;
    private final OperazioneBatchClient operazioneBatchClient;
    private final ExternalCallMetricsRecorder externalCallMetricsRecorder;
    private final Executor operazioniTriggerExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    public RiconciliazioneUpsertService(IncassoRepository incassoRepository,
                                        PagamentoRepository pagamentoRepository,
                                        DominioRepository dominioRepository,
                                        IbanAccreditoRepository ibanAccreditoRepository,
                                        FrRepository frRepository,
                                        RiscossioniResolver riscossioniResolver,
                                        RiconciliazioneMapper mapper,
                                        CurrentOperatorService currentOperatorService,
                                        AclAuthorizer aclAuthorizer,
                                        OperazioniProperties operazioniProperties,
                                        OperazioneBatchClient operazioneBatchClient,
                                        ExternalCallMetricsRecorder externalCallMetricsRecorder,
                                        @Qualifier("operazioniTriggerExecutor") Executor operazioniTriggerExecutor) {
        this.incassoRepository = incassoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.dominioRepository = dominioRepository;
        this.ibanAccreditoRepository = ibanAccreditoRepository;
        this.frRepository = frRepository;
        this.riscossioniResolver = riscossioniResolver;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
        this.aclAuthorizer = aclAuthorizer;
        this.operazioniProperties = operazioniProperties;
        this.operazioneBatchClient = operazioneBatchClient;
        this.externalCallMetricsRecorder = externalCallMetricsRecorder;
        this.operazioniTriggerExecutor = operazioniTriggerExecutor;
    }

    @Transactional
    public UpsertResult upsert(String idDominio, String id, NuovaRiconciliazione body) {
        aclAuthorizer.requireScrittura(AclServizio.RENDICONTAZIONI_E_INCASSI);
        OperatoreCorrente operatore = currentOperatorService.get();

        if (body.getIuv() != null) {
            throw new BadRequestException(
                    "Il campo 'iuv' non è ammesso in scrittura in V2: la modalità di riversamento singolo non è più supportata (usa 'idFlusso' o 'causale' cumulativa).");
        }

        Dominio dominio = dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new BadRequestException("Dominio '" + idDominio + "' non censito in anagrafica."));
        if (!DominioVisibilita.isVisibile(dominio.getId(), operatore)) {
            throw new AccessDeniedException(
                    "L'operatore '" + operatore.principal() + "' non ha visibilità in scrittura sul dominio '" + idDominio + "'.");
        }

        String idFlussoDiretto = body.getIdFlusso();
        String causale = body.getCausale();
        String idfRisolto = risolviIdFlusso(idFlussoDiretto, causale);

        if (body.getIbanAccredito() != null
                && !ibanAccreditoRepository.existsByDominio_IdAndCodIban(dominio.getId(), body.getIbanAccredito())) {
            throw new BadRequestException("L'IBAN di accredito '" + body.getIbanAccredito()
                    + "' non è censito sul dominio '" + idDominio + "'.");
        }

        Incasso incasso;
        boolean accettata;
        var esistente = incassoRepository.findByCodDominioAndIdentificativo(idDominio, id);
        if (esistente.isPresent()) {
            Incasso corrente = esistente.get();
            verificaDatiAccessoriCoerenti(corrente, causale, idfRisolto, body);
            if (!STATO_ERRORE.equals(corrente.getStato())) {
                incasso = corrente;
                accettata = false;
            } else {
                // Ri-accodare da ERRORE non e' un'operazione fidata sullo stato
                // pregresso: tra il tentativo fallito e questo retry il flusso
                // puo' essere stato riconciliato da un altro incasso, diventare
                // anomalo, o il suo importo puo' essere cambiato — stesso
                // pre-flight di una registrazione nuova.
                verificaFlusso(idDominio, idfRisolto, body.getImporto());
                incasso = riscrivi(corrente, causale, idfRisolto, body);
                accettata = true;
            }
        } else {
            verificaFlusso(idDominio, idfRisolto, body.getImporto());
            incasso = inserisci(idDominio, id, causale, idfRisolto, body);
            accettata = true;
        }

        if (accettata) {
            schedulaHookPostCommit();
        }

        return new UpsertResult(costruisciDto(dominio, incasso), accettata, incasso.getId());
    }

    // ----- pre-flight ---------------------------------------------------------------

    private String risolviIdFlusso(String idFlussoDiretto, String causale) {
        if (idFlussoDiretto != null) {
            return idFlussoDiretto;
        }
        if (causale == null) {
            throw new BadRequestException(
                    "Nella richiesta di registrazione non è stato specificato né 'idFlusso' né 'causale': uno dei due è obbligatorio.");
        }
        String cumulativo = CausaleIncassoParser.getRiferimentoIncassoCumulativo(causale);
        if (cumulativo != null) {
            return cumulativo;
        }
        String singolo = CausaleIncassoParser.getRiferimentoIncassoSingolo(causale);
        if (singolo != null) {
            throw new BadRequestException("La causale indica un riversamento singolo (IUV estratto: '" + singolo
                    + "'): questa modalità non è più supportata in scrittura in V2, sono ammessi solo riversamenti cumulativi (idFlusso).");
        }
        throw new BadRequestException(
                "La causale non è conforme alle specifiche AgID (SACIV 1.2.1): impossibile estrarre un identificativo di flusso.");
    }

    private void verificaDatiAccessoriCoerenti(Incasso esistente, String causaleRichiesta, String idfRisolto,
                                               NuovaRiconciliazione body) {
        if (causaleRichiesta != null && esistente.getCausale() != null
                && !causaleRichiesta.trim().equalsIgnoreCase(esistente.getCausale())) {
            throw new ConflictException(
                    "Riconciliazione già registrata con causale diversa: '" + esistente.getCausale() + "'.");
        }
        if (!idfRisolto.equals(esistente.getCodFlussoRendicontazione())) {
            throw new ConflictException("Riconciliazione già registrata con idFlusso diverso: '"
                    + esistente.getCodFlussoRendicontazione() + "'.");
        }
        if (body.getSct() != null && !body.getSct().equals(esistente.getSct())) {
            throw new ConflictException("Riconciliazione già registrata con sct diverso: '" + esistente.getSct() + "'.");
        }
        if (differisce(body.getImporto(), esistente.getImporto())) {
            throw new ConflictException("Riconciliazione già registrata con importo diverso: " + esistente.getImporto() + ".");
        }
    }

    private void verificaFlusso(String idDominio, String idFlusso, Double importoRichiesto) {
        Fr fr = frRepository.findByCodDominioAndCodFlussoAndObsoletoFalse(idDominio, idFlusso)
                .orElseThrow(() -> new NotFoundException(
                        "Flusso di rendicontazione '" + idFlusso + "' non trovato per il dominio '" + idDominio + "'."));
        if (fr.getIdIncasso() != null) {
            throw new ConflictException("Il flusso '" + idFlusso
                    + "' risulta già riconciliato da un'altra riconciliazione (id incasso tecnico: " + fr.getIdIncasso() + ").");
        }
        if (!STATO_FR_ACCETTATA.equals(fr.getStato())) {
            throw new ConflictException("Il flusso di rendicontazione '" + idFlusso
                    + "' presenta anomalie (stato: " + fr.getStato() + "): non può essere riconciliato.");
        }
        if (differisce(importoRichiesto, fr.getImportoTotalePagamenti())) {
            throw new ConflictException("L'importo indicato (" + importoRichiesto
                    + ") non corrisponde al totale pagamenti del flusso (" + fr.getImportoTotalePagamenti() + ").");
        }
    }

    private static boolean differisce(Double a, Double b) {
        return BigDecimal.valueOf(a).setScale(2, RoundingMode.HALF_UP)
                .compareTo(BigDecimal.valueOf(b).setScale(2, RoundingMode.HALF_UP)) != 0;
    }

    // ----- persistenza ---------------------------------------------------------------

    private Incasso inserisci(String idDominio, String id, String causale, String idfRisolto, NuovaRiconciliazione body) {
        Incasso i = new Incasso();
        i.setIdentificativo(id);
        i.setCodDominio(idDominio);
        applicaCampi(i, causale, idfRisolto, body);
        i.setDataOraIncasso(OffsetDateTime.now()); // placeholder, sovrascritto sotto col clock del DB
        Incasso saved = incassoRepository.saveAndFlush(i);
        touchClockDb(saved);
        return saved;
    }

    /** Reiterazione su ERRORE: riscrive tutte le colonne (stessa semantica di {@code IncassiBD.updateIncasso()} V1/v3). */
    private Incasso riscrivi(Incasso esistente, String causale, String idfRisolto, NuovaRiconciliazione body) {
        applicaCampi(esistente, causale, idfRisolto, body);
        Incasso saved = incassoRepository.saveAndFlush(esistente);
        touchClockDb(saved);
        return saved;
    }

    private static void applicaCampi(Incasso i, String causale, String idfRisolto, NuovaRiconciliazione body) {
        i.setTrn(idfRisolto);
        i.setCausale(causale);
        i.setImporto(body.getImporto());
        i.setDataValuta(body.getDataValuta());
        i.setDataContabile(body.getDataContabile());
        i.setIbanAccredito(body.getIbanAccredito());
        i.setSct(body.getSct());
        i.setIuv(null);
        i.setCodFlussoRendicontazione(idfRisolto);
        i.setStato(STATO_NUOVO);
        i.setDescrizioneStato(null);
    }

    /** Sovrascrive {@code data_ora_incasso} col clock del DB e sincronizza l'istanza in memoria. */
    private void touchClockDb(Incasso saved) {
        incassoRepository.touchDataOraIncasso(saved.getId());
        entityManager.refresh(saved);
    }

    // ----- hook post-commit ------------------------------------------------------------

    /**
     * Registrato SOLO se {@code accettata}: la riga è stata inserita/riaccodata
     * in questa transazione. Il fallimento del trigger non deve mai far
     * fallire il PUT — vedi {@link #triggerBatch}.
     */
    private void schedulaHookPostCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operazioniProperties.find(ID_OPERAZIONE_TRIGGER)
                        .filter(c -> c.getUrl() != null && c.isAbilitata())
                        .ifPresentOrElse(
                                config -> CompletableFuture.runAsync(() -> triggerBatch(config), operazioniTriggerExecutor),
                                () -> log.debug("Operazione '{}' non censita o non abilitata nel catalogo: hook noop.",
                                        ID_OPERAZIONE_TRIGGER));
            }
        });
    }

    private void triggerBatch(OperazioneConfig config) {
        try {
            externalCallMetricsRecorder.record("operazioni", ID_OPERAZIONE_TRIGGER,
                    () -> operazioneBatchClient.run(config.getUrl(), false));
        } catch (RuntimeException e) {
            log.warn("Hook riconciliazioni: trigger fallito verso {} (non bloccante): {}",
                    config.getUrl(), e.getMessage(), e);
        }
    }

    // ----- risposta -------------------------------------------------------------

    private Riconciliazione costruisciDto(Dominio dominio, Incasso incasso) {
        Map<String, Dominio> domini = Map.of(dominio.getCodDominio(), dominio);
        List<Pagamento> riscossioni = pagamentoRepository.findByIdIncassoOrderByDataPagamentoAsc(incasso.getId());
        Map<Long, Rpt> rptById = riscossioniResolver.loadRpt(riscossioni);
        Map<Long, SingoloVersamento> svById = riscossioniResolver.loadSingoliVersamenti(riscossioni);
        return mapper.toDetail(incasso, domini, riscossioni, rptById, svById);
    }
}
