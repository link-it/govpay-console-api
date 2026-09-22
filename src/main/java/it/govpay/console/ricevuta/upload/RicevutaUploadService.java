package it.govpay.console.ricevuta.upload;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    private static final String SEGMENTO_RICEVUTE = "ricevute";
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
        // sui fallimenti"). I campi dello stato restano null se il fallimento avviene
        // prima che siano determinabili (es. ACL negata).
        StatoUpload stato = new StatoUpload();
        try {
            // ACL prima di leggere/bufferizzare il body: un operatore senza il diritto
            // non deve poter forzare il server a leggere un payload arbitrario.
            aclAuthorizer.requireScrittura(AclServizio.PAGAMENTI);

            stato.contenuto = resolveContenuto(request, multipartFile);
            validateSize(stato.contenuto.bytes());

            byte[] normalizzato = normalizer.normalize(stato.contenuto.bytes());
            RicevutaRiconosciuta esito = formatDetector.detect(normalizzato);
            stato.formato = esito.formato();
            stato.idDominio = esito.idDominio();
            stato.iuv = esito.iuv();
            stato.idRicevuta = esito.idRicevuta();

            Preparazione preparazione = preparaInvio(normalizzato, stato);

            if (!DominioVisibilita.isVisibile(preparazione.idDominioTecnico(), operatore)) {
                throw new AccessDeniedException("L'operatore '" + operatore.principal()
                        + "' non e' autorizzato sul dominio '" + stato.idDominio + "'.");
            }

            // Rifiuta subito una ri-sottomissione di una ricevuta gia' acquisita, senza
            // contattare api-pagopa. Non e' ridondante con la gestione di
            // PAA_RECEIPT_DUPLICATA in PaForNodeClient: quella copre il retry dello
            // stesso tentativo dopo una risposta persa, questo un nuovo caricamento
            // a distanza di tempo.
            if (rptRepository.findByKey(stato.idDominio, stato.iuv, stato.idRicevuta).isPresent()) {
                throw new ConflictException("Ricevuta gia' acquisita: idDominio=" + stato.idDominio
                        + ", iuv=" + stato.iuv + ", idRicevuta=" + stato.idRicevuta + ".");
            }

            inviaAApiPagopa(normalizzato, preparazione, stato);

            ResponseEntity<Ricevuta> risposta =
                    rileggiEDataRisposta(stato.idDominio, stato.iuv, stato.idRicevuta, request);
            stato.idRpt = rptRepository.findByKey(stato.idDominio, stato.iuv, stato.idRicevuta)
                    .map(Rpt::getId).orElse(null);
            registraAudit(stato, String.valueOf(risposta.getStatusCode().value()), operatore, request);
            return risposta;
        } catch (RuntimeException e) {
            registraAudit(stato, esitoDa(e), operatore, request);
            throw e;
        }
    }

    /**
     * Ramo XML: il payload normalizzato e' gia' il corpo da inoltrare, va solo
     * validato contro {@code paForNode.xsd} e il dominio risolto a partire dal
     * {@code fiscalCode} letto dal documento. Ramo JSON: la conversione produce
     * la request e risolve gia' il dominio.
     */
    private Preparazione preparaInvio(byte[] normalizzato, StatoUpload stato) {
        if (stato.formato == RicevutaFormato.JSON_PAGOPA) {
            RicevutaJsonConversione conversione = jsonConverter.convert(normalizzato);
            return new Preparazione(conversione.request(), conversione.idDominio());
        }
        if (stato.idDominio == null || stato.idDominio.isBlank()) {
            throw new BadRequestException(
                    "Impossibile determinare 'idDominio' (fiscalCode) dalla ricevuta caricata.");
        }
        // Valida contro paForNode.xsd (es. la <xsd:choice> IBAN/MBDAttachment):
        // l'oggetto risultante e' scartato, il corpo inoltrato resta il normalizzato
        // cosi' com'e' (PaForNodeClient.inviaRicevutaXml, nessun remarshal).
        xmlValidator.validate(normalizzato, stato.formato);
        String codDominioAtteso = stato.idDominio;
        Dominio dominio = dominioRepository.findByCodDominio(codDominioAtteso)
                .orElseThrow(() -> new UnprocessableEntityException(
                        "Dominio sconosciuto: " + codDominioAtteso));
        return new Preparazione(null, dominio.getId());
    }

    /**
     * Un fallimento di trasporto non e' un esito definitivo: se la rilettura
     * trova la ricevuta, l'acquisizione e' comunque andata a buon fine
     * nonostante l'errore client-side (issue #59 par. 7).
     */
    private void inviaAApiPagopa(byte[] normalizzato, Preparazione preparazione, StatoUpload stato) {
        try {
            if (preparazione.jsonRequest() != null) {
                paForNodeClient.inviaRicevutaV2(preparazione.jsonRequest());
            } else {
                paForNodeClient.inviaRicevutaXml(normalizzato, stato.formato);
            }
        } catch (PaForNodeTransportException e) {
            Optional<Rpt> rilettura = rptRepository.findByKey(stato.idDominio, stato.iuv, stato.idRicevuta);
            if (rilettura.isEmpty()) {
                if (e.isTimeout()) {
                    throw new PaForNodeTimeoutException(
                            "Timeout durante l'invio della ricevuta a api-pagopa.", e);
                }
                throw new PaForNodeUnavailableException(
                        "Errore di trasporto durante l'invio della ricevuta a api-pagopa.", e);
            }
        }
    }

    /**
     * Esito di {@link #preparaInvio}: {@code jsonRequest} valorizzata nel solo
     * ramo JSON — nel ramo XML resta nulla e il corpo da inoltrare e' il
     * payload normalizzato cosi' com'e'.
     */
    private record Preparazione(it.gov.pagopa.pagopa_api.pa.pafornode.PaSendRTV2Request jsonRequest,
                                Long idDominioTecnico) {
    }

    /**
     * Accumulatore dei dati di audit man mano che diventano determinabili: serve
     * perche' l'audit va registrato anche quando l'upload fallisce a meta' strada,
     * col poco che si e' riusciti a estrarre fino a quel punto.
     */
    private static final class StatoUpload {
        private String idDominio;
        private String iuv;
        private String idRicevuta;
        private RicevutaFormato formato;
        private Long idRpt;
        private UploadContenuto contenuto;
    }

    private ResponseEntity<Ricevuta> rileggiEDataRisposta(String idDominio, String iuv, String idRicevuta,
                                                           HttpServletRequest request) {
        Ricevuta dto = ricevutaService.getDetail(idDominio, iuv, idRicevuta, request);
        return ResponseEntity.created(locationCanonica(idDominio, iuv, idRicevuta)).body(dto);
    }

    private static URI locationCanonica(String idDominio, String iuv, String idRicevuta) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .pathSegment(SEGMENTO_RICEVUTE, idDominio, iuv, idRicevuta)
                .build()
                .toUri();
    }

    private void registraAudit(StatoUpload stato, String esito,
                               OperatoreCorrente operatore, HttpServletRequest request) {
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", stato.idDominio);
        dettaglio.put("iuv", stato.iuv);
        dettaglio.put("idRicevuta", stato.idRicevuta);
        dettaglio.put("nomeFile", stato.contenuto != null ? stato.contenuto.nomeFile() : null);
        dettaglio.put("dimensione", stato.contenuto != null ? stato.contenuto.bytes().length : null);
        dettaglio.put("formato", stato.formato != null ? stato.formato.name() : null);
        dettaglio.put("esito", esito);
        auditService.registra(AZIONE_AUDIT_CARICA, stato.idRpt != null ? stato.idRpt : 0L,
                dettaglio, operatore, request);
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

    /**
     * {@code equals}/{@code hashCode}/{@code toString} generati confronterebbero
     * {@code bytes} per riferimento: ridefiniti sul contenuto (S6218). Il
     * {@code toString} riporta la sola dimensione, non il payload.
     */
    private record UploadContenuto(byte[] bytes, String nomeFile) {

        @Override
        public boolean equals(Object o) {
            return o instanceof UploadContenuto altro
                    && Arrays.equals(bytes, altro.bytes)
                    && Objects.equals(nomeFile, altro.nomeFile);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(bytes) + Objects.hashCode(nomeFile);
        }

        @Override
        public String toString() {
            return "UploadContenuto[bytes=" + (bytes != null ? bytes.length : 0)
                    + " byte, nomeFile=" + nomeFile + "]";
        }
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
