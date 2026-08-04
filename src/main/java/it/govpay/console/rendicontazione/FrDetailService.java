package it.govpay.console.rendicontazione;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.entity.Fr;
import it.govpay.console.entity.FrXml;
import it.govpay.console.model.FlussoRendicontazione;
import it.govpay.console.repository.FrRepository;
import it.govpay.console.repository.FrXmlRepository;
import it.govpay.console.repository.RendicontazioneRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotAcceptableMediaTypeException;
import it.govpay.console.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.TimeUnit;

/**
 * Dettaglio canonico {@code GET /flussi-rendicontazione/{idDominio}/{idFlusso}/{idPsp}/{revisione}}
 * con content negotiation JSON/XML sullo stesso path (a differenza delle
 * ricevute non c'è una sub-resource dedicata: qui non c'è PDF da offrire).
 *
 * <p>Per il ramo XML: come {@code RicevutaService}, il servizio scrive
 * direttamente su {@link HttpServletResponse} e ritorna {@code null}; il
 * controller risponde con {@code ResponseEntity.ok().build()}.
 */
@Service
public class FrDetailService {

    private final FrRepository frRepository;
    private final FrXmlRepository frXmlRepository;
    private final RendicontazioneRepository rendicontazioneRepository;
    private final FrMapper mapper;
    private final CurrentOperatorService currentOperatorService;

    public FrDetailService(FrRepository frRepository,
                           FrXmlRepository frXmlRepository,
                           RendicontazioneRepository rendicontazioneRepository,
                           FrMapper mapper,
                           CurrentOperatorService currentOperatorService) {
        this.frRepository = frRepository;
        this.frXmlRepository = frXmlRepository;
        this.rendicontazioneRepository = rendicontazioneRepository;
        this.mapper = mapper;
        this.currentOperatorService = currentOperatorService;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlussoRendicontazione> get(String idDominio, String idFlusso, String idPsp,
                                                     Long revisione, HttpServletRequest request,
                                                     HttpServletResponse response) {
        Fr fr = loadVisibile(idDominio, idFlusso, idPsp, revisione);
        MediaType chosen = chooseContentType(request);

        if (MediaType.APPLICATION_XML.equals(chosen)) {
            byte[] xml = frXmlRepository.findById(fr.getId()).map(FrXml::getXml).orElse(null);
            if (xml == null || xml.length == 0) {
                throw new NotFoundException("XML non disponibile per il flusso di rendicontazione "
                        + "(flusso storico con soli metadati archiviati).");
            }
            streamBytes(xml, "flusso-" + idFlusso + "-r" + revisione + ".xml", response);
            return null;
        }

        FrPeriodo periodo = rendicontazioneRepository.findPeriodo(fr.getId());
        FlussoRendicontazione dto = mapper.toDetail(fr, periodo);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(dto);
    }

    /** Carica il flusso per quaterna applicando l'ACL (404 anti-leak, nessun audit sul 404). */
    private Fr loadVisibile(String idDominio, String idFlusso, String idPsp, Long revisione) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Fr fr = frRepository.findByCodDominioAndCodFlussoAndCodPspAndRevisione(idDominio, idFlusso, idPsp, revisione)
                .orElseThrow(() -> new NotFoundException(notFoundMessage(idDominio, idFlusso, idPsp, revisione)));
        if (!DominioVisibilita.isVisibile(fr.getIdDominio(), operatore)) {
            throw new NotFoundException(notFoundMessage(idDominio, idFlusso, idPsp, revisione));
        }
        return fr;
    }

    private static String notFoundMessage(String idDominio, String idFlusso, String idPsp, Long revisione) {
        return "Flusso di rendicontazione non trovato: idDominio=" + idDominio + ", idFlusso=" + idFlusso
                + ", idPsp=" + idPsp + ", revisione=" + revisione;
    }

    private static void streamBytes(byte[] bytes, String filename, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        try {
            response.getOutputStream().write(bytes);
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Content negotiation via header {@code Accept}: JSON di default, XML se richiesto, altrimenti 406. */
    private static MediaType chooseContentType(HttpServletRequest request) {
        String header = request != null ? request.getHeader(HttpHeaders.ACCEPT) : null;
        if (header == null || header.isBlank() || header.contains("*/*")
                || header.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)) {
            return MediaType.APPLICATION_JSON;
        }
        if (header.toLowerCase().contains(MediaType.APPLICATION_XML_VALUE)) {
            return MediaType.APPLICATION_XML;
        }
        throw new NotAcceptableMediaTypeException(
                "Accept '" + header + "' non supportato: ammessi application/json e application/xml.");
    }
}
