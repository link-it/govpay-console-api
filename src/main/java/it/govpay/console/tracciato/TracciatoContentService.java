package it.govpay.console.tracciato;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code GET .../tracciati/{id}/richiesta} e {@code .../esito}: entrambi
 * sono un passthrough diretto dei byte gia' salvati ({@code raw_richiesta}/
 * {@code raw_esito} — esattamente il payload originale, nessuna
 * ri-serializzazione), con content negotiation limitata al solo formato
 * nativo del tracciato (JSON o CSV, mai entrambi: nessuna conversione
 * lossless).
 */
@Service
public class TracciatoContentService {

    public static final String AZIONE_AUDIT_RICHIESTA_VISUALIZZA = "TRACCIATO_RICHIESTA_VISUALIZZA";

    private final TracciatoRepository repository;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;

    public TracciatoContentService(TracciatoRepository repository,
                                   CurrentOperatorService currentOperatorService,
                                   AuditService auditService) {
        this.repository = repository;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public void richiesta(Long id, HttpServletRequest request, HttpServletResponse response) {
        Tracciato tracciato = loadVisibile(id);
        MediaType mediaType = negoziaMediaType(request, tracciato.getFormato());
        stream(tracciato.getRawRichiesta(), "richiesta-" + id, mediaType, response);

        OperatoreCorrente operatore = currentOperatorService.get();
        auditService.registra(AZIONE_AUDIT_RICHIESTA_VISUALIZZA, id, Map.of(), operatore, request);
    }

    @Transactional(readOnly = true)
    public void esito(Long id, HttpServletRequest request, HttpServletResponse response) {
        Tracciato tracciato = loadVisibile(id);
        byte[] esito = tracciato.getRawEsito();
        if (esito == null || esito.length == 0) {
            throw new NotFoundException("Esito non ancora disponibile per il tracciato " + id
                    + ": elaborazione non completata.");
        }
        MediaType mediaType = negoziaMediaType(request, tracciato.getFormato());
        stream(esito, "esito-" + id, mediaType, response);
    }

    private Tracciato loadVisibile(Long id) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Tracciato tracciato = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tracciato non trovato: " + id));
        if (!DominioVisibilita.isVisibile(tracciato.getDominio().getId(), operatore)) {
            throw new NotFoundException("Tracciato non trovato: " + id);
        }
        return tracciato;
    }

    /** Solo il formato nativo del tracciato e' disponibile: nessuna conversione lossless JSON/CSV. */
    private static MediaType negoziaMediaType(HttpServletRequest request, String formatoDb) {
        MediaType nativo = FormatoTracciato.JSON.getValue().equals(formatoDb)
                ? MediaType.APPLICATION_JSON
                : MediaType.valueOf("text/csv");
        String header = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (header == null || header.isBlank() || header.contains("*/*")
                || header.toLowerCase().contains(nativo.toString())) {
            return nativo;
        }
        throw new NotAcceptableMediaTypeException("Accept '" + header + "' non compatibile con il formato nativo "
                + "del tracciato (" + nativo + "): nessuna conversione lossless disponibile.");
    }

    private static void stream(byte[] bytes, String baseFilename, MediaType mediaType, HttpServletResponse response) {
        String ext = MediaType.APPLICATION_JSON.equals(mediaType) ? "json" : "csv";
        response.setContentType(mediaType.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + baseFilename + "." + ext + "\"");
        try {
            response.getOutputStream().write(bytes);
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
