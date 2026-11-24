package it.govpay.console.avviso;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.metrics.ExternalCallMetricsRecorder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.pendenza.PendenzaLettura;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.stampe.client.model.PaymentNotice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AvvisoService {

    private static final Logger log = LoggerFactory.getLogger(AvvisoService.class);

    private final PendenzaLettura lettura;
    private final IbanPostaleResolver ibanPostaleResolver;
    private final AvvisoMapper avvisoMapper;
    private final AvvisoPdfPayloadMapper pdfPayloadMapper;
    private final StampeClient stampeClient;
    private final CurrentOperatorService currentOperatorService;
    private final ExternalCallMetricsRecorder externalCallMetricsRecorder;

    public AvvisoService(PendenzaLettura lettura,
                         IbanPostaleResolver ibanPostaleResolver,
                         AvvisoMapper avvisoMapper,
                         AvvisoPdfPayloadMapper pdfPayloadMapper,
                         StampeClient stampeClient,
                         CurrentOperatorService currentOperatorService,
                         ExternalCallMetricsRecorder externalCallMetricsRecorder) {
        this.lettura = lettura;
        this.ibanPostaleResolver = ibanPostaleResolver;
        this.avvisoMapper = avvisoMapper;
        this.pdfPayloadMapper = pdfPayloadMapper;
        this.currentOperatorService = currentOperatorService;
        this.stampeClient = stampeClient;
        this.externalCallMetricsRecorder = externalCallMetricsRecorder;
    }

    /**
     * Per il ramo {@code application/json} ritorna una normale
     * {@link ResponseEntity}. Per il ramo {@code application/pdf} fa
     * <b>vero streaming</b> direttamente sulla {@link HttpServletResponse}
     * (header + body) e ritorna {@code null}: il controller risponde solo con
     * un wrapper di status code, gli header e il body sono gia' stati scritti
     * dal service.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Avviso> get(String idA2A,
                                      String idPendenza,
                                      LinguaSecondaria lingua,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        OperatoreCorrente operatore = currentOperatorService.get();
        log.debug("getAvviso idA2A={} idPendenza={} lingua={} operatore={}",
                idA2A, idPendenza, lingua, operatore.principal());

        PendenzaLettura.Letta letta = lettura.leggiVisibile(idA2A, idPendenza, operatore);
        Pendenza pendenza = letta.pendenza();
        Dominio dominio = letta.contesto().dominio();

        if (pendenza.getNumeroAvviso() == null || pendenza.getNumeroAvviso().isBlank()) {
            throw new NotFoundException(
                    "Pendenza priva di numeroAvviso: avviso non disponibile.");
        }

        MediaType chosen = chooseContentType(request);
        if (MediaType.APPLICATION_PDF.equals(chosen)) {
            if (AvvisoPdfPayloadMapper.isPendenzaMbt(pendenza)) {
                throw new AvvisoMbtException(
                        "Avviso PDF non disponibile per pendenze con Marca da Bollo Telematica.");
            }
            if (AvvisoPdfPayloadMapper.isAnagraficaDebitoreAssente(pendenza)) {
                throw new AvvisoAnagraficaAssenteException(
                        "Avviso PDF non disponibile: anagrafica del debitore assente.");
            }
            IbanAccredito postale = ibanPostaleResolver.risolvi(pendenza);
            PaymentNotice payload = pdfPayloadMapper.toPaymentNotice(pendenza, dominio, postale, lingua);
            String filename = buildFilename(dominio, pendenza);
            log.debug("getAvviso PDF streaming idPendenza={} lingua={}", idPendenza, lingua);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            try {
                OutputStream output = response.getOutputStream();
                externalCallMetricsRecorder.record("stampe", "payment_notice",
                        () -> stampeClient.streamPaymentNotice(payload, output));
                response.flushBuffer();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }
        Avviso avviso = avvisoMapper.toAvviso(pendenza, dominio);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(avviso);
    }

    /** Il numero avviso e' unico: distingue anche le rate di una stessa opzione. */
    private static String buildFilename(Dominio dominio, Pendenza p) {
        String dom = dominio != null ? dominio.getCodDominio() : "AVV";
        return dom + "_" + p.getNumeroAvviso() + ".pdf";
    }

    private static MediaType chooseContentType(HttpServletRequest request) {
        String header = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (header == null || header.isBlank() || header.contains("*/*")
                || header.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
            return MediaType.APPLICATION_JSON;
        }
        if (header.toLowerCase().contains(MediaType.APPLICATION_PDF_VALUE)) {
            return MediaType.APPLICATION_PDF;
        }
        throw new NotAcceptableMediaTypeException(
                "Accept '" + header + "' non supportato: ammessi application/json e application/pdf.");
    }

    /**
     * Helper esposto per tracing/log: lista dei content-type ammessi.
     */
    public static List<MediaType> supportedMediaTypes() {
        return List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PDF);
    }
}
