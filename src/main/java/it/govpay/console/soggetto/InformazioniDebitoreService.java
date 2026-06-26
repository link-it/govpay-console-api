package it.govpay.console.soggetto;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Soggetto;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servizio dell'endpoint {@code GET /pendenze/{idA2A}/{idPendenza}/informazioniDebitore}.
 *
 * <p>Espone i dati anagrafici/contatti del debitore associato a una pendenza.
 *
 * <p>Ogni 200 viene tracciato in {@code gp_audit} con azione
 * {@link #AZIONE_AUDIT_VISUALIZZA}. L'audit viene scritto <i>dopo</i> il mapping
 * (prima del return) per non sporcare la tabella in caso di errori a monte.
 *
 * <p>ACL: 404 anti-leak (stesso pattern di {@code AvvisoService} e
 * {@code RicevutaService}).
 */
@Service
public class InformazioniDebitoreService {

    public static final String AZIONE_AUDIT_VISUALIZZA = "PENDENZA_VISUALIZZA_DEBITORE";

    private static final Logger log = LoggerFactory.getLogger(InformazioniDebitoreService.class);

    private final VersamentoRepository repository;
    private final SoggettoMapper soggettoMapper;
    private final AuditService auditService;
    private final CurrentOperatorService currentOperatorService;

    public InformazioniDebitoreService(VersamentoRepository repository,
                                       SoggettoMapper soggettoMapper,
                                       AuditService auditService,
                                       CurrentOperatorService currentOperatorService) {
        this.repository = repository;
        this.soggettoMapper = soggettoMapper;
        this.auditService = auditService;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public Soggetto get(String idA2A, String idPendenza, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getInformazioniDebitore idA2A={} idPendenza={} operatore={}",
                idA2A, idPendenza, operatore.principal());

        Versamento versamento = repository.findDetail(idA2A, idPendenza)
                .orElseThrow(() -> new NotFoundException(
                        "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza));

        if (!isVisibile(versamento, operatore)) {
            log.debug("getInformazioniDebitore ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={}",
                    idA2A, idPendenza);
            throw new NotFoundException(
                    "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
        }

        Soggetto soggetto = soggettoMapper.toSoggetto(versamento);

        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idA2A", idA2A);
        dettaglio.put("idPendenza", idPendenza);
        dettaglio.put("identificativoDebitore", soggetto.getIdentificativo());
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, versamento.getId(),
                dettaglio, operatore, request);

        return soggetto;
    }

    private static boolean isVisibile(Versamento v, OperatoreCorrente operatore) {
        if (!isDominioOrUoVisible(v, operatore)) {
            return false;
        }
        if (!operatore.tuttiITipiVersamento()) {
            if (v.getTipoVersamento() == null) {
                return false;
            }
            return operatore.idTipiVersamentoVisibili().contains(v.getTipoVersamento().getId());
        }
        return true;
    }

    private static boolean isDominioOrUoVisible(Versamento v, OperatoreCorrente operatore) {
        if (operatore.tuttiIDomini()) {
            return true;
        }
        if (v.getDominio() == null) {
            return false;
        }
        if (operatore.idDominiInteri().contains(v.getDominio().getId())) {
            return true;
        }
        if (v.getUnitaOperativa() != null
                && operatore.idUoVisibili().contains(v.getUnitaOperativa().getId())) {
            return true;
        }
        return false;
    }
}
