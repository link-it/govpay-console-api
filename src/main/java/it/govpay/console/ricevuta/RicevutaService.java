package it.govpay.console.ricevuta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.avviso.StampeClient;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.ricevuta.pagopa.RptRtJsonConverter;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import it.govpay.stampe.client.model.Receipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servizio del dominio ricevute:
 * <ul>
 *   <li>{@link #listByPendenza} — lista metadata-only delle RT di una pendenza;</li>
 *   <li>{@link #getDetail} — dettaglio canonico {@link Ricevuta} (top-level);</li>
 *   <li>{@link #getRpt}/{@link #getRt} — sub-resource con content negotiation:
 *       JSON (conversione), XML (originale dal DB) e, per la sola RT, PDF
 *       (microservizio {@code govpay-stampe}).</li>
 * </ul>
 *
 * <p>ACL: 404 anti-leak su dominio non visibile o tipo versamento non autorizzato
 * (via {@link VersamentoVisibilita}).
 *
 * <p>Per i rami binari (XML/PDF): come {@code /avviso}, scriviamo direttamente su
 * {@link HttpServletResponse} e restituiamo {@code null}; il controller risponde
 * con {@code ResponseEntity.ok().build()}.
 */
@Service
public class RicevutaService {

    private static final Logger log = LoggerFactory.getLogger(RicevutaService.class);

    public static final String AZIONE_AUDIT_VISUALIZZA = "RICEVUTA_VISUALIZZA";

    private final RptRepository rptRepository;
    private final VersamentoRepository versamentoRepository;
    private final RicevutaMapper ricevutaMapper;
    private final RicevutaPdfPayloadMapper pdfPayloadMapper;
    private final StampeClient stampeClient;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final RptRtJsonConverter rptRtJsonConverter;

    public RicevutaService(RptRepository rptRepository,
                           VersamentoRepository versamentoRepository,
                           RicevutaMapper ricevutaMapper,
                           RicevutaPdfPayloadMapper pdfPayloadMapper,
                           StampeClient stampeClient,
                           CurrentOperatorService currentOperatorService,
                           AuditService auditService,
                           RptRtJsonConverter rptRtJsonConverter) {
        this.rptRepository = rptRepository;
        this.versamentoRepository = versamentoRepository;
        this.ricevutaMapper = ricevutaMapper;
        this.pdfPayloadMapper = pdfPayloadMapper;
        this.stampeClient = stampeClient;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.rptRtJsonConverter = rptRtJsonConverter;
    }

    /**
     * Dettaglio canonico della RT identificata da {@code (idDominio, iuv,
     * idRicevuta)} per l'endpoint top-level {@code GET /ricevute/{...}}. ACL
     * post-fetch con 404 anti-leak (nessun audit sul 404). Ogni 200 traccia
     * {@code RICEVUTA_VISUALIZZA} in {@code gp_audit} perché il body include
     * {@code rpt}/{@code rt} con anagrafica del soggetto pagatore.
     */
    @Transactional(readOnly = true)
    public Ricevuta getDetail(String idDominio, String iuv, String idRicevuta, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Rpt rpt = rptRepository.findByKey(idDominio, iuv, idRicevuta)
                .orElseThrow(() -> new NotFoundException(
                        "Ricevuta non trovata: idDominio=" + idDominio + ", iuv=" + iuv
                                + ", idRicevuta=" + idRicevuta));
        if (!VersamentoVisibilita.isVisibile(rpt.getVersamento(), operatore)) {
            log.debug("getRicevuta ACL nega l'accesso (404 anti-leak) idDominio={} iuv={} idRicevuta={}",
                    idDominio, iuv, idRicevuta);
            throw new NotFoundException(
                    "Ricevuta non trovata: idDominio=" + idDominio + ", iuv=" + iuv
                            + ", idRicevuta=" + idRicevuta);
        }
        Ricevuta dto = ricevutaMapper.toRicevuta(rpt);
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, rpt.getId(),
                auditDettaglio(idDominio, iuv, idRicevuta, "ricevuta"), operatore, request);
        return dto;
    }

    private static Map<String, Object> auditDettaglio(String idDominio, String iuv,
                                                      String idRicevuta, String risorsa) {
        Map<String, Object> dettaglio = new LinkedHashMap<>();
        dettaglio.put("idDominio", idDominio);
        dettaglio.put("iuv", iuv);
        dettaglio.put("idRicevuta", idRicevuta);
        dettaglio.put("risorsa", risorsa);
        return dettaglio;
    }

    private static Map<String, Object> auditDettaglio(String idDominio, String iuv,
                                                      String idRicevuta, String risorsa, String formato) {
        Map<String, Object> dettaglio = auditDettaglio(idDominio, iuv, idRicevuta, risorsa);
        dettaglio.put("formato", formato);
        return dettaglio;
    }

    /**
     * Lista (metadata-only) delle RT di una pendenza, ordinata per
     * {@code dataPagamento DESC}. Pendenza inesistente o non visibile per ACL
     * → 404 (anti-leak); pendenza senza RT → lista vuota.
     */
    @Transactional(readOnly = true)
    public List<RicevutaSummary> listByPendenza(String idA2A, String idPendenza) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Versamento versamento = versamentoRepository.findDetail(idA2A, idPendenza)
                .orElseThrow(() -> new NotFoundException(
                        "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza));
        if (!VersamentoVisibilita.isVisibile(versamento, operatore)) {
            throw new NotFoundException(
                    "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
        }
        return rptRepository.findByPendenza(idA2A, idPendenza).stream()
                .map(ricevutaMapper::toSummary)
                .toList();
    }

    /**
     * Sub-resource {@code GET /ricevute/{idDominio}/{iuv}/{idRicevuta}/rpt}.
     * Content negotiation {@code application/json} (RPT convertita) /
     * {@code application/xml} (RPT originale dal DB). Niente PDF: govpay-stampe non
     * stampa la richiesta (allineato a V1). Ogni 200 traccia
     * {@code RICEVUTA_VISUALIZZA} con {@code risorsa=rpt} e {@code formato}.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getRpt(String idDominio, String iuv, String idRicevuta,
                                         HttpServletRequest request, HttpServletResponse response) {
        Rpt rpt = loadVisibile(idDominio, iuv, idRicevuta);
        MediaType chosen = chooseContentType(request, false);
        if (MediaType.APPLICATION_XML.equals(chosen)) {
            byte[] xml = requireBytes(rpt.getXmlRpt(), "RPT XML non disponibile per la ricevuta.");
            streamBytes(xml, MediaType.APPLICATION_XML_VALUE, "rpt-" + iuv + ".xml", response);
            audit(rpt, idDominio, iuv, idRicevuta, "rpt", "xml", request);
            return null;
        }
        Map<String, Object> json = rptRtJsonConverter.toRptMap(rpt);
        if (json == null) {
            throw new NotFoundException("RPT non disponibile per la ricevuta (acquisita in standin).");
        }
        audit(rpt, idDominio, iuv, idRicevuta, "rpt", "json", request);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /**
     * Sub-resource {@code GET /ricevute/{idDominio}/{iuv}/{idRicevuta}/rt}.
     * Content negotiation {@code application/json} (RT convertita) /
     * {@code application/xml} (RT originale) / {@code application/pdf} (stampa via
     * govpay-stampe). Ogni 200 traccia {@code RICEVUTA_VISUALIZZA} con
     * {@code risorsa=rt} e {@code formato}.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getRt(String idDominio, String iuv, String idRicevuta,
                                        HttpServletRequest request, HttpServletResponse response) {
        Rpt rpt = loadVisibile(idDominio, iuv, idRicevuta);
        MediaType chosen = chooseContentType(request, true);
        if (MediaType.APPLICATION_XML.equals(chosen)) {
            byte[] xml = requireBytes(rpt.getXmlRt(), "RT XML non disponibile per la ricevuta.");
            streamBytes(xml, MediaType.APPLICATION_XML_VALUE, "rt-" + iuv + ".xml", response);
            audit(rpt, idDominio, iuv, idRicevuta, "rt", "xml", request);
            return null;
        }
        if (MediaType.APPLICATION_PDF.equals(chosen)) {
            streamReceiptPdf(rpt, "ricevuta-" + iuv + ".pdf", response);
            audit(rpt, idDominio, iuv, idRicevuta, "rt", "pdf", request);
            return null;
        }
        Map<String, Object> json = rptRtJsonConverter.toRtMap(rpt);
        if (json == null) {
            throw new NotFoundException("RT non disponibile per la ricevuta.");
        }
        audit(rpt, idDominio, iuv, idRicevuta, "rt", "json", request);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    /** Carica la RT per chiave applicando l'ACL (404 anti-leak, nessun audit sul 404). */
    private Rpt loadVisibile(String idDominio, String iuv, String idRicevuta) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Rpt rpt = rptRepository.findByKey(idDominio, iuv, idRicevuta)
                .orElseThrow(() -> new NotFoundException(notFoundMessage(idDominio, iuv, idRicevuta)));
        if (!VersamentoVisibilita.isVisibile(rpt.getVersamento(), operatore)) {
            throw new NotFoundException(notFoundMessage(idDominio, iuv, idRicevuta));
        }
        return rpt;
    }

    private void audit(Rpt rpt, String idDominio, String iuv, String idRicevuta,
                       String risorsa, String formato, HttpServletRequest request) {
        auditService.registra(AZIONE_AUDIT_VISUALIZZA, rpt.getId(),
                auditDettaglio(idDominio, iuv, idRicevuta, risorsa, formato),
                currentOperatorService.get(), request);
    }

    private static byte[] requireBytes(byte[] bytes, String messaggio404) {
        if (bytes == null || bytes.length == 0) {
            throw new NotFoundException(messaggio404);
        }
        return bytes;
    }

    private static void streamBytes(byte[] bytes, String contentType, String filename,
                                    HttpServletResponse response) {
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        try {
            response.getOutputStream().write(bytes);
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void streamReceiptPdf(Rpt rpt, String filename, HttpServletResponse response) {
        Receipt payload = pdfPayloadMapper.toReceipt(rpt);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        try {
            stampeClient.streamReceipt(payload, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String notFoundMessage(String idDominio, String iuv, String idRicevuta) {
        return "Ricevuta non trovata: idDominio=" + idDominio + ", iuv=" + iuv
                + ", idRicevuta=" + idRicevuta;
    }

    /**
     * Content negotiation via header {@code Accept}: JSON di default, XML sempre
     * ammesso, PDF solo se {@code allowPdf}. Accept non supportato → 406.
     */
    private static MediaType chooseContentType(HttpServletRequest request, boolean allowPdf) {
        String header = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (header == null || header.isBlank() || header.contains("*/*")
                || header.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
            return MediaType.APPLICATION_JSON;
        }
        String lower = header.toLowerCase();
        if (lower.contains(MediaType.APPLICATION_XML_VALUE)) {
            return MediaType.APPLICATION_XML;
        }
        if (allowPdf && lower.contains(MediaType.APPLICATION_PDF_VALUE)) {
            return MediaType.APPLICATION_PDF;
        }
        String ammessi = allowPdf
                ? "application/json, application/xml e application/pdf"
                : "application/json e application/xml";
        throw new NotAcceptableMediaTypeException(
                "Accept '" + header + "' non supportato: ammessi " + ammessi + ".");
    }
}
