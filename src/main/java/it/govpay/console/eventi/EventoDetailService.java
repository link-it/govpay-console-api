package it.govpay.console.eventi;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Fr;
import it.govpay.console.model.Evento;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;

/**
 * Dettaglio metadata-only {@code GET /eventi/{id}}. Nessun audit: non espone
 * dati personali ne' credenziali (quelli sono sui sub-resource dedicati).
 * 404 indistinguibile tra "non esiste" e "non visibile per ACL" (anti-leak).
 */
@Service
public class EventoDetailService {

    private final EventoGdeClient client;
    private final EventoMapper mapper;
    private final EventoAcl eventoAcl;
    private final CurrentOperatorService currentOperatorService;
    private final FrRepository frRepository;

    public EventoDetailService(EventoGdeClient client, EventoMapper mapper,
            EventoAcl eventoAcl, CurrentOperatorService currentOperatorService, FrRepository frRepository) {
        this.client = client;
        this.mapper = mapper;
        this.eventoAcl = eventoAcl;
        this.currentOperatorService = currentOperatorService;
        this.frRepository = frRepository;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Evento> get(Long id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        it.govpay.gde.client.beans.Evento evento = client.getEventoById(id);

        if (!eventoAcl.isVisibile(evento.getIdDominio(), operatore)) {
            throw new NotFoundException("Evento non trovato: " + id);
        }

        Fr frCorrelato = evento.getIdFr() != null ? frRepository.findById(evento.getIdFr()).orElse(null) : null;

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(mapper.toDetail(evento, frCorrelato));
    }
}
