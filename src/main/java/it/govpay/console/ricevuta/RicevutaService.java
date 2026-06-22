package it.govpay.console.ricevuta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.avviso.StampeClient;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.model.RicevutaSummary;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import it.govpay.stampe.client.model.Receipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servizio dell'endpoint {@code GET /pendenze/{idA2A}/{idPendenza}/ricevuta}.
 *
 * <p>Content negotiation 3-way:
 * <ul>
 *   <li>{@code application/json} (default) → metadati {@link Ricevuta} dal DB;</li>
 *   <li>{@code application/xml} → {@code xml_rt} originale dal DB, streaming;</li>
 *   <li>{@code application/pdf} → generato dal microservizio {@code govpay-stampe},
 *       streaming.</li>
 * </ul>
 *
 * <p>ACL: gli stessi pattern di {@code AvvisoService} (404 anti-leak su dominio
 * non visibile o tipo versamento non autorizzato).
 *
 * <p>Per il ramo PDF: come {@code /avviso}, scriviamo direttamente su
 * {@link HttpServletResponse} (status + headers + body) e restituiamo
 * {@code null}; il controller risponde con {@code ResponseEntity.ok().build()}.
 */
@Service
public class RicevutaService {

    private static final Logger log = LoggerFactory.getLogger(RicevutaService.class);

    private final RptRepository rptRepository;
    private final VersamentoRepository versamentoRepository;
    private final RicevutaMapper ricevutaMapper;
    private final RicevutaPdfPayloadMapper pdfPayloadMapper;
    private final StampeClient stampeClient;
    private final CurrentOperatorService currentOperatorService;

    public RicevutaService(RptRepository rptRepository,
                           VersamentoRepository versamentoRepository,
                           RicevutaMapper ricevutaMapper,
                           RicevutaPdfPayloadMapper pdfPayloadMapper,
                           StampeClient stampeClient,
                           CurrentOperatorService currentOperatorService) {
        this.rptRepository = rptRepository;
        this.versamentoRepository = versamentoRepository;
        this.ricevutaMapper = ricevutaMapper;
        this.pdfPayloadMapper = pdfPayloadMapper;
        this.stampeClient = stampeClient;
        this.currentOperatorService = currentOperatorService;
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
        if (!isVisibile(versamento, operatore)) {
            throw new NotFoundException(
                    "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
        }
        return rptRepository.findByPendenza(idA2A, idPendenza).stream()
                .map(RicevutaService::toSummary)
                .toList();
    }

    private static RicevutaSummary toSummary(Rpt rpt) {
        RicevutaSummary s = new RicevutaSummary(rpt.getCodDominio(), rpt.getIuv(), rpt.getCcp());
        s.setDataPagamento(rpt.getDataMsgRicevuta());
        s.setImportoTotalePagato(rpt.getImportoTotalePagato());
        s.setEsito(rpt.getCodEsitoPagamento());
        s.setIdPsp(rpt.getCodPsp());
        return s;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Ricevuta> get(String idA2A,
                                        String idPendenza,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getRicevuta idA2A={} idPendenza={} operatore={}",
                idA2A, idPendenza, operatore.principal());

        Rpt rpt = rptRepository.findPrincipale(idA2A, idPendenza)
                .orElseThrow(() -> new NotFoundException(
                        "Ricevuta non trovata per la pendenza idA2A=" + idA2A
                                + ", idPendenza=" + idPendenza));

        if (!isVisibile(rpt.getVersamento(), operatore)) {
            log.debug("getRicevuta ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={}",
                    idA2A, idPendenza);
            throw new NotFoundException(
                    "Ricevuta non trovata per la pendenza idA2A=" + idA2A
                            + ", idPendenza=" + idPendenza);
        }

        MediaType chosen = chooseContentType(request);

        if (MediaType.APPLICATION_XML.equals(chosen)) {
            return streamXml(rpt, response);
        }
        if (MediaType.APPLICATION_PDF.equals(chosen)) {
            return streamPdf(rpt, response);
        }
        Ricevuta dto = ricevutaMapper.toRicevuta(rpt);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(dto);
    }

    private ResponseEntity<Ricevuta> streamXml(Rpt rpt, HttpServletResponse response) {
        byte[] xml = rpt.getXmlRt();
        if (xml == null || xml.length == 0) {
            throw new NotFoundException("RT XML non disponibile per la pendenza.");
        }
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + buildFilenameXml(rpt) + "\"");
        try {
            response.getOutputStream().write(xml);
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private ResponseEntity<Ricevuta> streamPdf(Rpt rpt, HttpServletResponse response) {
        Receipt payload = pdfPayloadMapper.toReceipt(rpt);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + buildFilenamePdf(rpt) + "\"");
        try {
            stampeClient.streamReceipt(payload, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    /** Filename allineato a V1 {@code RppController.java:496}. */
    private static String buildFilenamePdf(Rpt rpt) {
        return rpt.getCodDominio() + "_" + rpt.getIuv() + "_" + rpt.getCcp() + ".pdf";
    }

    /** Stesso pattern del PDF ma estensione {@code .xml}. */
    private static String buildFilenameXml(Rpt rpt) {
        return rpt.getCodDominio() + "_" + rpt.getIuv() + "_" + rpt.getCcp() + ".xml";
    }

    private static MediaType chooseContentType(HttpServletRequest request) {
        String header = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (header == null || header.isBlank() || header.contains("*/*")
                || header.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
            return MediaType.APPLICATION_JSON;
        }
        String lower = header.toLowerCase();
        if (lower.contains(MediaType.APPLICATION_XML_VALUE)) {
            return MediaType.APPLICATION_XML;
        }
        if (lower.contains(MediaType.APPLICATION_PDF_VALUE)) {
            return MediaType.APPLICATION_PDF;
        }
        throw new NotAcceptableMediaTypeException(
                "Accept '" + header + "' non supportato: ammessi application/json, "
                        + "application/xml e application/pdf.");
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

    public static List<MediaType> supportedMediaTypes() {
        return List.of(MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_XML,
                MediaType.APPLICATION_PDF);
    }
}
