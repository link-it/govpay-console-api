package it.govpay.console.tracciato;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Tracciato;
import it.govpay.console.model.AclServizio;
import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.NuovaPendenzaTracciato;
import it.govpay.console.model.TracciatoPendenze;
import it.govpay.console.model.TracciatoPendenzePost;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.TipoVersamentoDominioRepository;
import it.govpay.console.repository.TracciatoRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.DominioVisibilita;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.audit.AuditService;
import it.govpay.console.web.BadRequestException;
import it.govpay.console.web.PayloadTooLargeException;
import it.govpay.console.web.UnprocessableEntityException;
import it.govpay.console.web.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /pendenze/tracciati}: content negotiation su {@code Content-Type}
 * (JSON/CSV/multipart), persistenza del tracciato e del contenuto originale.
 * Nessuna elaborazione delle pendenze qui: il tracciato resta {@code IN_ATTESA},
 * lo raccoglie il polling schedulato del batch di govpay-core (nessun trigger
 * immediato attraversabile da un processo separato come questo, a differenza
 * di V1 dove core e API erano nella stessa JVM).
 */
@Service
public class TracciatoUploadService {

    public static final String AZIONE_AUDIT_CARICATO = "TRACCIATO_CARICATO";

    private static final long DEFAULT_MAX_FILE_SIZE_MB = 50;

    private final TracciatoRepository tracciatoRepository;
    private final DominioRepository dominioRepository;
    private final TipoVersamentoDominioRepository tipoVersamentoDominioRepository;
    private final OperatoreRepository operatoreRepository;
    private final TracciatoMapper mapper;
    private final AuditService auditService;
    private final AclAuthorizer aclAuthorizer;
    private final CurrentOperatorService currentOperatorService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final long maxFileSizeBytes;

    public TracciatoUploadService(TracciatoRepository tracciatoRepository,
                                  DominioRepository dominioRepository,
                                  TipoVersamentoDominioRepository tipoVersamentoDominioRepository,
                                  OperatoreRepository operatoreRepository,
                                  TracciatoMapper mapper,
                                  AuditService auditService,
                                  AclAuthorizer aclAuthorizer,
                                  CurrentOperatorService currentOperatorService,
                                  ObjectMapper objectMapper,
                                  Validator validator,
                                  @org.springframework.beans.factory.annotation.Value(
                                          "${govpay.tracciati.max-file-size-mb:" + DEFAULT_MAX_FILE_SIZE_MB + "}")
                                  long maxFileSizeMb) {
        this.tracciatoRepository = tracciatoRepository;
        this.dominioRepository = dominioRepository;
        this.tipoVersamentoDominioRepository = tipoVersamentoDominioRepository;
        this.operatoreRepository = operatoreRepository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.aclAuthorizer = aclAuthorizer;
        this.currentOperatorService = currentOperatorService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
    }

    @Transactional
    public TracciatoPendenze upload(HttpServletRequest request,
                                    MultipartFile multipartFile,
                                    String idDominioParam,
                                    String idTipoPendenzaParam,
                                    Boolean stampaAvvisiParam,
                                    FormatoTracciato formatoParam) {
        aclAuthorizer.requireScrittura(AclServizio.PENDENZE);
        OperatoreCorrente operatore = currentOperatorService.get();

        String contentType = request.getContentType() == null ? "" : request.getContentType();
        boolean stampaAvvisi = stampaAvvisiParam == null || stampaAvvisiParam;

        UploadContenuto contenuto = resolveContenuto(request, multipartFile, contentType, formatoParam);
        validateSize(contenuto.bytes());

        String idDominio = resolveIdDominio(contenuto, idDominioParam);
        if (idDominio == null || idDominio.isBlank()) {
            throw new BadRequestException("Il parametro 'idDominio' e' obbligatorio per il caricamento CSV/multipart CSV "
                    + "(per il JSON deve essere valorizzato nel body).");
        }

        if (contenuto.formato() == FormatoTracciato.JSON) {
            validateJsonBody(contenuto.bytes(), idDominio);
        }

        Dominio dominio = dominioRepository.findByCodDominio(idDominio)
                .orElseThrow(() -> new UnprocessableEntityException("Dominio sconosciuto: " + idDominio));
        if (!DominioVisibilita.isVisibile(dominio.getId(), operatore)) {
            throw new AccessDeniedException("L'operatore '" + operatore.principal()
                    + "' non e' autorizzato sul dominio '" + idDominio + "'.");
        }

        if (idTipoPendenzaParam != null && !idTipoPendenzaParam.isBlank()
                && !tipoVersamentoDominioRepository.existsByDominio_IdAndTipoVersamento_CodTipoVersamento(
                        dominio.getId(), idTipoPendenzaParam)) {
            throw new UnprocessableEntityException(
                    "Tipo pendenza '" + idTipoPendenzaParam + "' sconosciuto per il dominio '" + idDominio + "'.");
        }

        Tracciato tracciato = new Tracciato();
        tracciato.setDominio(dominio);
        tracciato.setCodTipoVersamento(idTipoPendenzaParam);
        tracciato.setFormato(contenuto.formato().getValue());
        tracciato.setTipo("PENDENZA");
        tracciato.setStato(TracciatoStatoMapper.STATO_ELABORAZIONE);
        tracciato.setDataCaricamento(OffsetDateTime.now());
        tracciato.setFileNameRichiesta(contenuto.nomeFile());
        tracciato.setBeanDati(serializeBeanDatiIniziale(stampaAvvisi));
        tracciato.setRawRichiesta(contenuto.bytes());
        operatoreRepository.findByIdUtenza(operatore.idUtenza()).ifPresent(tracciato::setOperatore);

        tracciato = tracciatoRepository.save(tracciato);

        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", idDominio);
        dettaglio.put("nomeFile", contenuto.nomeFile());
        dettaglio.put("dimensione", contenuto.bytes().length);
        dettaglio.put("formato", contenuto.formato().getValue());
        auditService.registra(AZIONE_AUDIT_CARICATO, tracciato.getId(), dettaglio, operatore, request);

        return mapper.toDto(tracciato);
    }

    private record UploadContenuto(byte[] bytes, FormatoTracciato formato, String nomeFile, String idDominioDaJson) {
    }

    private UploadContenuto resolveContenuto(HttpServletRequest request,
                                             MultipartFile multipartFile,
                                             String contentType,
                                             FormatoTracciato formatoParam) {
        if (contentType.startsWith("multipart/form-data")) {
            if (multipartFile == null || multipartFile.isEmpty()) {
                throw new BadRequestException("Campo 'file' mancante o vuoto nel body multipart.");
            }
            FormatoTracciato formato = formatoParam != null ? formatoParam : formatoDaNomeFile(multipartFile.getOriginalFilename());
            byte[] bytes = readBytes(multipartFile);
            String idDominioDaJson = formato == FormatoTracciato.JSON ? extractIdDominio(bytes) : null;
            return new UploadContenuto(bytes, formato, multipartFile.getOriginalFilename(), idDominioDaJson);
        }
        if (contentType.startsWith("application/json")) {
            byte[] bytes = readBytes(request);
            return new UploadContenuto(bytes, FormatoTracciato.JSON, null, extractIdDominio(bytes));
        }
        if (contentType.startsWith("text/csv")) {
            byte[] bytes = readBytes(request);
            validateCsvWellFormed(bytes);
            return new UploadContenuto(bytes, FormatoTracciato.CSV, null, null);
        }
        throw new UnsupportedMediaTypeException("Content-Type non supportato: " + contentType
                + ". Attesi 'application/json', 'text/csv' o 'multipart/form-data'.");
    }

    private FormatoTracciato formatoDaNomeFile(String filename) {
        if (filename == null) {
            throw new BadRequestException(
                    "Impossibile determinare il formato del file multipart: filename assente e '?formato' non specificato.");
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".json")) {
            return FormatoTracciato.JSON;
        }
        if (lower.endsWith(".csv")) {
            return FormatoTracciato.CSV;
        }
        throw new BadRequestException(
                "Impossibile determinare il formato dal filename '" + filename + "': usa '?formato=JSON|CSV'.");
    }

    /** Parsing minimo (solo idDominio) per risolvere il dominio target prima della validazione completa: evita di deserializzare due volte per gli errori di formato puro. */
    private String extractIdDominio(byte[] bytes) {
        try {
            TracciatoPendenzePost post = objectMapper.readValue(bytes, TracciatoPendenzePost.class);
            return post.getIdDominio();
        } catch (JacksonException e) {
            throw new BadRequestException("JSON non valido: " + e.getMessage());
        }
    }

    private String resolveIdDominio(UploadContenuto contenuto, String idDominioParam) {
        if (contenuto.formato() == FormatoTracciato.JSON) {
            return contenuto.idDominioDaJson();
        }
        return idDominioParam;
    }

    private void validateSize(byte[] bytes) {
        if (bytes.length > maxFileSizeBytes) {
            throw new PayloadTooLargeException(
                    "Il file supera la dimensione massima consentita di " + (maxFileSizeBytes / (1024 * 1024)) + "MB.");
        }
    }

    private void validateCsvWellFormed(byte[] bytes) {
        if (bytes.length == 0) {
            throw new BadRequestException("Il contenuto CSV e' vuoto.");
        }
        String text;
        try {
            text = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BadRequestException("Il contenuto CSV non e' testo UTF-8 valido.");
        }
        if (text.isBlank()) {
            throw new BadRequestException("Il contenuto CSV e' vuoto.");
        }
    }

    /**
     * Valida il body JSON contro i vincoli Bean Validation degli schemi
     * generati (required/pattern/minItems/...): non passa dal binding
     * automatico di Spring perche' letto manualmente dallo stream (vedi
     * {@link #resolveContenuto}), quindi la validazione va invocata a mano.
     */
    private void validateJsonBody(byte[] bytes, String idDominioRoot) {
        TracciatoPendenzePost post;
        try {
            post = objectMapper.readValue(bytes, TracciatoPendenzePost.class);
        } catch (JacksonException e) {
            throw new BadRequestException("JSON non valido: " + e.getMessage());
        }
        Set<ConstraintViolation<TracciatoPendenzePost>> violations = validator.validate(post);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        List<NuovaPendenzaTracciato> inserimenti = post.getInserimenti();
        if (inserimenti != null) {
            for (NuovaPendenzaTracciato riga : inserimenti) {
                String rigaIdDominio = riga.getIdDominio();
                if (rigaIdDominio != null && !rigaIdDominio.isBlank() && !rigaIdDominio.equals(idDominioRoot)) {
                    throw new BadRequestException(
                            "Tracciati multi-dominio non supportati: la riga con idA2A='" + riga.getIdA2A()
                                    + "', idPendenza='" + riga.getIdPendenza() + "' dichiara idDominio='" + rigaIdDominio
                                    + "' diverso dall'idDominio del tracciato ('" + idDominioRoot + "').");
                }
            }
        }
        boolean nessunaOperazione = (post.getInserimenti() == null || post.getInserimenti().isEmpty())
                && (post.getAnnullamenti() == null || post.getAnnullamenti().isEmpty());
        if (nessunaOperazione) {
            throw new BadRequestException("Il tracciato non contiene ne' 'inserimenti' ne' 'annullamenti'.");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Impossibile leggere il file caricato: " + e.getMessage());
        }
    }

    private static byte[] readBytes(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new BadRequestException("Impossibile leggere il body della richiesta: " + e.getMessage());
        }
    }

    private String serializeBeanDatiIniziale(boolean stampaAvvisi) {
        TracciatoBeanDati beanDati = new TracciatoBeanDati();
        beanDati.setStepElaborazione("NUOVO");
        beanDati.setStampaAvvisi(stampaAvvisi);
        return objectMapper.writeValueAsString(beanDati);
    }
}
