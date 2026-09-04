package it.govpay.console.ricevuta.upload;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Rpt;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.ricevuta.RicevutaService;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.ConflictException;
import it.govpay.console.web.PayloadTooLargeException;
import it.govpay.console.web.UnprocessableEntityException;
import it.govpay.console.web.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /ricevute}: caricamento di una RT da cruscotto.
 * Orchestratore che mette insieme normalizzazione/riconoscimento
 * formato (§C), conversione JSON (§D) e client verso {@code api-pagopa} (§B),
 * seguendo l'ordine dell'issue (§E):
 * <ol>
 *   <li>ACL scrittura {@code Pagamenti};</li>
 *   <li>riconosce formato ed estrae la tupla; ramo XML: normalizza soltanto;
 *       ramo JSON: converte (risolve anche {@code Dominio});</li>
 *   <li>dominio della RT visibile all'operatore → 403;</li>
 *   <li>pre-flight duplicato → 409;</li>
 *   <li>invio a {@code api-pagopa};</li>
 *   <li>outcome negativo o fault → 422 (gia' mappato da {@link PaForNodeClient});</li>
 *   <li>errore di trasporto → rilettura, poi 201 o 502/504;</li>
 *   <li>successo → rilettura e risposta {@link Ricevuta} + {@code Location}.</li>
 * </ol>
 */
@Service
public class RicevutaUploadService {

    public static final String AZIONE_AUDIT_CARICA = "RICEVUTA_CARICA";

    private static final String CANONICAL_PATH = "/ricevute/{idDominio}/{iuv}/{idRicevuta}";
    private static final long DEFAULT_MAX_SIZE_BYTES = 1_048_576;

    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final RicevutaPayloadNormalizer normalizer;
    private final RicevutaFormatDetector formatDetector;
    private final RicevutaXmlValidator xmlValidator;
    private final RicevutaJsonConverter jsonConverter;
    private final PaForNodeClient paForNodeClient;
    private final RptRepository rptRepository;
    private final DominioRepository dominioRepository;
    private final RicevutaService ricevutaService;
    private final AuditService auditService;
    private final long maxSizeBytes;

    public RicevutaUploadService(AclAuthorizer aclAuthorizer,
                                 CurrentOperatorService currentOperatorService,
                                 RicevutaPayloadNormalizer normalizer,
                                 RicevutaFormatDetector formatDetector,
                                 RicevutaXmlValidator xmlValidator,
                                 RicevutaJsonConverter jsonConverter,
                                 PaForNodeClient paForNodeClient,
                                 RptRepository rptRepository,
                                 DominioRepository dominioRepository,
                                 RicevutaService ricevutaService,
                                 AuditService auditService,
                                 @Value("${app.ricevute.upload.max-size-bytes:" + DEFAULT_MAX_SIZE_BYTES + "}")
                                 long maxSizeBytes) {
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.normalizer = normalizer;
        this.formatDetector = formatDetector;
        this.xmlValidator = xmlValidator;
        this.jsonConverter = jsonConverter;
        this.paForNodeClient = paForNodeClient;
        this.rptRepository = rptRepository;
        this.dominioRepository = dominioRepository;
        this.ricevutaService = ricevutaService;
        this.auditService = auditService;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional
    public ResponseEntity<Ricevuta> upload(HttpServletRequest request, MultipartFile multipartFile) {
        OperatoreCorrente operatore = currentOperatorService.get();

        // Tutto cio' che segue e' dentro il try/audit: un rifiuto in una qualsiasi fase
        // (ACL, riconoscimento formato/conversione JSON, visibilita', duplicato, invio)
        // e' un evento di sicurezza quanto un successo (issue #59 par. F, "audit anche
        // sui fallimenti"). idDominio/iuv/idRicevuta/formato/contenuto restano null se
        // il fallimento avviene prima che siano determinabili (es. ACL negata).
        String idDominio = null;
        String iuv = null;
        String idRicevuta = null;
        RicevutaFormato formato = null;
        Long idRptAudit = null;
        UploadContenuto contenuto = null;
        try {
            // ACL prima di leggere/bufferizzare il body: un operatore senza il diritto
            // non deve poter forzare il server a leggere un payload arbitrario.
            aclAuthorizer.requireScrittura(AclServizio.PAGAMENTI);

            contenuto = resolveContenuto(request, multipartFile);
            validateSize(contenuto.bytes());

            byte[] normalizzato = normalizer.normalize(contenuto.bytes());
            RicevutaRiconosciuta esito = formatDetector.detect(normalizzato);
            formato = esito.formato();
            idDominio = esito.idDominio();
            iuv = esito.iuv();
            idRicevuta = esito.idRicevuta();

            byte[] xmlDaInviare = null;
            it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request jsonRequest = null;
            Long idDominioTecnico;

            if (formato == RicevutaFormato.JSON_PAGOPA) {
                RicevutaJsonConversione conversione = jsonConverter.convert(normalizzato);
                jsonRequest = conversione.request();
                idDominioTecnico = conversione.idDominio();
            } else {
                xmlDaInviare = normalizzato;
                if (idDominio == null || idDominio.isBlank()) {
                    throw new BadRequestException(
                            "Impossibile determinare 'idDominio' (fiscalCode) dalla ricevuta caricata.");
                }
                // Valida contro paForNode.xsd (es. la <xsd:choice> IBAN/MBDAttachment):
                // l'oggetto risultante e' scartato, il corpo inoltrato resta xmlDaInviare
                // cosi' com'e' (PaForNodeClient.inviaRicevutaXml, nessun remarshal).
                xmlValidator.validate(normalizzato, formato);
                String codDominioAtteso = idDominio;
                Dominio dominio = dominioRepository.findByCodDominio(idDominio)
                        .orElseThrow(() -> new UnprocessableEntityException(
                                "Dominio sconosciuto: " + codDominioAtteso));
                idDominioTecnico = dominio.getId();
            }

            if (!DominioVisibilita.isVisibile(idDominioTecnico, operatore)) {
                throw new AccessDeniedException("L'operatore '" + operatore.principal()
                        + "' non e' autorizzato sul dominio '" + idDominio + "'.");
            }

            if (rptRepository.findByKey(idDominio, iuv, idRicevuta).isPresent()) {
                throw new ConflictException("Ricevuta gia' acquisita: idDominio=" + idDominio
                        + ", iuv=" + iuv + ", idRicevuta=" + idRicevuta + ".");
            }

            try {
                if (jsonRequest != null) {
                    paForNodeClient.inviaRicevutaV2(jsonRequest);
                } else {
                    paForNodeClient.inviaRicevutaXml(xmlDaInviare, formato);
                }
            } catch (PaForNodeTransportException e) {
                Optional<Rpt> rilettura = rptRepository.findByKey(idDominio, iuv, idRicevuta);
                if (rilettura.isEmpty()) {
                    if (e.isTimeout()) {
                        throw new PaForNodeTimeoutException(
                                "Timeout durante l'invio della ricevuta a api-pagopa.", e);
                    }
                    throw new PaForNodeUnavailableException(
                            "Errore di trasporto durante l'invio della ricevuta a api-pagopa.", e);
                }
                // Rilettura riuscita nel frattempo: l'acquisizione e' comunque andata a
                // buon fine nonostante il fallimento client-side (issue #59 par. 7).
            }

            ResponseEntity<Ricevuta> risposta = rileggiEDataRisposta(idDominio, iuv, idRicevuta, request);
            idRptAudit = rptRepository.findByKey(idDominio, iuv, idRicevuta).map(Rpt::getId).orElse(null);
            registraAudit(idDominio, iuv, idRicevuta, idRptAudit, contenuto, formato,
                    String.valueOf(risposta.getStatusCode().value()), operatore, request);
            return risposta;
        } catch (RuntimeException e) {
            registraAudit(idDominio, iuv, idRicevuta, idRptAudit, contenuto, formato,
                    esitoDa(e), operatore, request);
            throw e;
        }
    }

    private ResponseEntity<Ricevuta> rileggiEDataRisposta(String idDominio, String iuv, String idRicevuta,
                                                           HttpServletRequest request) {
        Ricevuta dto = ricevutaService.getDetail(idDominio, iuv, idRicevuta, request);
        return ResponseEntity.created(locationCanonica(idDominio, iuv, idRicevuta)).body(dto);
    }

    private static URI locationCanonica(String idDominio, String iuv, String idRicevuta) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(CANONICAL_PATH)
                .buildAndExpand(idDominio, iuv, idRicevuta)
                .toUri();
    }

    private void registraAudit(String idDominio, String iuv, String idRicevuta, Long idRpt,
                               UploadContenuto contenuto, RicevutaFormato formato, String esito,
                               OperatoreCorrente operatore, HttpServletRequest request) {
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", idDominio);
        dettaglio.put("iuv", iuv);
        dettaglio.put("idRicevuta", idRicevuta);
        dettaglio.put("nomeFile", contenuto != null ? contenuto.nomeFile() : null);
        dettaglio.put("dimensione", contenuto != null ? contenuto.bytes().length : null);
        dettaglio.put("formato", formato != null ? formato.name() : null);
        dettaglio.put("esito", esito);
        auditService.registra(AZIONE_AUDIT_CARICA, idRpt != null ? idRpt : 0L, dettaglio, operatore, request);
    }

    private static String esitoDa(RuntimeException e) {
        if (e instanceof AccessDeniedException) {
            return "403";
        }
        if (e instanceof ConflictException) {
            return "409";
        }
        if (e instanceof UnprocessableEntityException) {
            return "422";
        }
        if (e instanceof PaForNodeUnavailableException) {
            return "502";
        }
        if (e instanceof PaForNodeTimeoutException) {
            return "504";
        }
        if (e instanceof PayloadTooLargeException) {
            return "413";
        }
        if (e instanceof UnsupportedMediaTypeException) {
            return "415";
        }
        if (e instanceof BadRequestException) {
            return "400";
        }
        return "500";
    }

    private record UploadContenuto(byte[] bytes, String nomeFile) {
    }

    private UploadContenuto resolveContenuto(HttpServletRequest request, MultipartFile multipartFile) {
        String contentType = request.getContentType() == null ? "" : request.getContentType();
        if (contentType.startsWith("multipart/form-data")) {
            if (multipartFile == null || multipartFile.isEmpty()) {
                throw new BadRequestException("Campo 'file' mancante o vuoto nel body multipart.");
            }
            return new UploadContenuto(readBoundedBytes(multipartFile), multipartFile.getOriginalFilename());
        }
        if (contentType.startsWith("application/xml") || contentType.startsWith("text/xml")
                || contentType.startsWith("application/json")) {
            return new UploadContenuto(readBoundedBytes(request), null);
        }
        throw new UnsupportedMediaTypeException("Content-Type non supportato: " + contentType
                + ". Attesi 'application/xml', 'text/xml', 'application/json' o 'multipart/form-data'.");
    }

    private void validateSize(byte[] bytes) {
        if (bytes.length == 0) {
            throw new BadRequestException("Il contenuto caricato e' vuoto.");
        }
        if (bytes.length > maxSizeBytes) {
            throw new PayloadTooLargeException(
                    "Il file supera la dimensione massima consentita di " + maxSizeBytes + " byte.");
        }
    }

    /**
     * Legge al piu' {@code maxSizeBytes + 1} byte dal file multipart: un file
     * oltre soglia fa scattare il 413 di {@link #validateSize} senza dover
     * prima bufferizzare per intero un payload potenzialmente enorme (a
     * differenza di {@code MultipartFile.getBytes()}, che alloca l'intero
     * contenuto prima di poter essere respinto — anche quando la parte e' su
     * disco lato container, {@code getBytes()} la porta comunque tutta in
     * heap). Il tetto Spring ({@code spring.servlet.multipart.max-file-size})
     * e' piu' ampio di questo limite applicativo (vedi {@code application.properties}),
     * quindi un multipart di alcuni MB arriva qui davvero: senza lettura
     * limitata verrebbe comunque bufferizzato per intero prima del 413.
     */
    private byte[] readBoundedBytes(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            long limit = maxSizeBytes + 1;
            return in.readNBytes((int) Math.min(limit, Integer.MAX_VALUE));
        } catch (IOException e) {
            throw new BadRequestException("Impossibile leggere il file caricato: " + e.getMessage());
        }
    }

    /**
     * Legge al piu' {@code maxSizeBytes + 1} byte dal body: un body oltre soglia
     * fa scattare il 413 di {@link #validateSize} senza dover prima bufferizzare
     * per intero un payload potenzialmente enorme (a differenza di
     * {@code readAllBytes()}, che alloca l'intero contenuto prima di poter essere
     * respinto).
     */
    private byte[] readBoundedBytes(HttpServletRequest request) {
        try {
            long limit = maxSizeBytes + 1;
            return request.getInputStream().readNBytes((int) Math.min(limit, Integer.MAX_VALUE));
        } catch (IOException e) {
            throw new BadRequestException("Impossibile leggere il body della richiesta: " + e.getMessage());
        }
    }
}
