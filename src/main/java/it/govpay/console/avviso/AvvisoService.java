package it.govpay.console.avviso;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;

import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.Avviso;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.repository.VersamentoRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import it.govpay.stampe.client.model.PaymentNotice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AvvisoService {

    private static final Logger log = LoggerFactory.getLogger(AvvisoService.class);

    private final VersamentoRepository repository;
    private final AvvisoMapper avvisoMapper;
    private final AvvisoPdfPayloadMapper pdfPayloadMapper;
    private final StampeClient stampeClient;
    private final CurrentOperatorService currentOperatorService;

    public AvvisoService(VersamentoRepository repository,
                         AvvisoMapper avvisoMapper,
                         AvvisoPdfPayloadMapper pdfPayloadMapper,
                         StampeClient stampeClient,
                         CurrentOperatorService currentOperatorService) {
        this.repository = repository;
        this.avvisoMapper = avvisoMapper;
        this.pdfPayloadMapper = pdfPayloadMapper;
        this.currentOperatorService = currentOperatorService;
        this.stampeClient = stampeClient;
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

        Versamento versamento = repository.findDetail(idA2A, idPendenza)
                .orElseThrow(() -> new NotFoundException(
                        "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza));

        if (!VersamentoVisibilita.isVisibile(versamento, operatore)) {
            log.debug("getAvviso ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={}",
                    idA2A, idPendenza);
            throw new NotFoundException(
                    "Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
        }

        if (versamento.getNumeroAvviso() == null || versamento.getNumeroAvviso().isBlank()) {
            throw new NotFoundException(
                    "Pendenza priva di numeroAvviso: avviso non disponibile.");
        }

        MediaType chosen = chooseContentType(request);
        if (MediaType.APPLICATION_PDF.equals(chosen)) {
            if (isPendenzaMbt(versamento)) {
                throw new AvvisoMbtException(
                        "Avviso PDF non disponibile per pendenze con Marca da Bollo Telematica.");
            }
            PaymentNotice payload = pdfPayloadMapper.toPaymentNotice(versamento, lingua);
            String filename = buildFilename(versamento);
            log.debug("getAvviso PDF streaming idPendenza={} lingua={}",
                    versamento.getCodVersamentoEnte(), lingua);
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            try {
                stampeClient.streamPaymentNotice(payload, response.getOutputStream());
                response.flushBuffer();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }
        Avviso avviso = avvisoMapper.toAvviso(versamento);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(avviso);
    }

    /**
     * Replica {@code VersamentoUtils.isPendenzaMBT} (govpay-381): la pendenza
     * e' una Marca da Bollo Telematica se almeno un singolo versamento ha
     * {@code hashDocumento}, {@code provinciaResidenza} e {@code tipoBollo}
     * tutti valorizzati.
     */
    private static boolean isPendenzaMbt(Versamento v) {
        if (v.getSingoliVersamenti() == null) {
            return false;
        }
        for (SingoloVersamento sv : v.getSingoliVersamenti()) {
            if (sv.getHashDocumento() != null
                    && sv.getProvinciaResidenza() != null
                    && sv.getTipoBollo() != null) {
                return true;
            }
        }
        return false;
    }

    private static String buildFilename(Versamento v) {
        // Per pendenze con documento aggiungiamo come suffisso il codRata (fallback: numeroAvviso)
    	// per evitare la collisione sui file delle N rate dello stesso documento.
        String dom = v.getDominio() != null ? v.getDominio().getCodDominio() : "AVV";
        if (v.getDocumento() != null && v.getDocumento().getCodDocumento() != null) {
            String rata = v.getCodRata() != null && !v.getCodRata().isBlank()
                    ? v.getCodRata()
                    : v.getNumeroAvviso();
            return dom + "_DOC_" + v.getDocumento().getCodDocumento() + "_" + rata + ".pdf";
        }
        return dom + "_" + v.getNumeroAvviso() + ".pdf";
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
