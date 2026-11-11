package it.govpay.console.dominio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Dominio;
import it.govpay.console.model.AclServizio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.security.AclAuthorizer;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PayloadTooLargeException;
import it.govpay.console.web.UnprocessableEntityException;
import it.govpay.console.web.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class DominioLogoService {

    private static final Logger log = LoggerFactory.getLogger(DominioLogoService.class);

    /**
     * Per gli SVG gia' in archivio: aperto direttamente nel browser, l'SVG non
     * esegue script e non carica risorse esterne (issue #124).
     */
    static final String CSP_SVG = "default-src 'none'; style-src 'unsafe-inline'; img-src data:";

    public static final String AZIONE_AUDIT_MODIFICA = "DOMINIO_LOGO_MODIFICA";
    public static final String AZIONE_AUDIT_RIMUOVI = "DOMINIO_LOGO_RIMUOVI";

    private final DominioRepository repository;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final long maxSizeBytes;
    private final long cacheMaxAgeSeconds;
    private final AclAuthorizer aclAuthorizer;

    public DominioLogoService(DominioRepository repository,
                              CurrentOperatorService currentOperatorService,
                              AuditService auditService,
                              @Value("${app.dominio.logo.max-size-bytes:262144}") long maxSizeBytes,
                              @Value("${app.dominio.logo.cache-max-age-seconds:86400}") long cacheMaxAgeSeconds,
                              AclAuthorizer aclAuthorizer) {
        this.repository = repository;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.maxSizeBytes = maxSizeBytes;
        this.cacheMaxAgeSeconds = cacheMaxAgeSeconds;
        this.aclAuthorizer = aclAuthorizer;
    }

    @Transactional(readOnly = true)
    public void getLogo(String idDominio, HttpServletResponse response) {
        aclAuthorizer.requireLettura(AclServizio.ANAGRAFICA_CREDITORE);
        Dominio dominio = load(idDominio);
        byte[] stored = dominio.getLogo();
        if (stored == null || stored.length == 0) {
            throw new NotFoundException("Il dominio '" + idDominio + "' non ha un logo.");
        }
        DominioLogoCodec.LogoArchiviato logo = DominioLogoCodec.leggi(stored).orElseThrow(() -> {
            log.warn("Logo del dominio [{}] non leggibile in archivio: non e' un'immagine in Base64. "
                    + "Trattato come logo assente.", idDominio);
            return new NotFoundException("Il dominio '" + idDominio + "' non ha un logo.");
        });
        byte[] image = logo.immagine();
        response.setContentType(logo.contentType());
        if (LogoMimeDetector.IMAGE_SVG.equals(logo.contentType())) {
            response.setHeader("Content-Security-Policy", CSP_SVG);
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + cacheMaxAgeSeconds);
        try {
            response.getOutputStream().write(image);
            response.flushBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional
    public void putLogo(String idDominio, byte[] content, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.ANAGRAFICA_CREDITORE);
        Dominio dominio = load(idDominio);

        if (content == null || content.length == 0) {
            throw new UnsupportedMediaTypeException("Il corpo della richiesta e' vuoto: caricare un'immagine "
                    + supportedTypesLabel() + ".");
        }
        if (content.length > maxSizeBytes) {
            throw new PayloadTooLargeException("Il logo supera la dimensione massima consentita di "
                    + maxSizeBytes + " byte (ricevuti " + content.length + ").");
        }
        String contentType = LogoMimeDetector.detect(content);
        if (!LogoMimeDetector.isSupported(contentType)) {
            throw new UnsupportedMediaTypeException("Tipo di immagine non supportato"
                    + (contentType != null ? " (" + contentType + ")" : "") + ": sono ammessi "
                    + supportedTypesLabel() + ".");
        }
        try {
            LogoRasterVerifier.verifica(content, contentType);
        } catch (LogoRasterVerifier.LogoNonValidoException e) {
            throw new UnprocessableEntityException("L'immagine " + contentType
                    + " non e' leggibile e non si potrebbe stampare: " + e.getMessage() + ".");
        }

        dominio.setLogo(DominioLogoCodec.encode(content));
        repository.save(dominio);

        audit(AZIONE_AUDIT_MODIFICA, dominio, contentType, request);
    }

    @Transactional
    public void deleteLogo(String idDominio, HttpServletRequest request) {
        aclAuthorizer.requireScrittura(AclServizio.ANAGRAFICA_CREDITORE);
        Dominio dominio = load(idDominio);
        if (dominio.getLogo() != null) {
            dominio.setLogo(null);
            repository.save(dominio);
        }
        audit(AZIONE_AUDIT_RIMUOVI, dominio, null, request);
    }

    private Dominio load(String idDominio) {
        return repository.findByCodDominio(idDominio)
                .orElseThrow(() -> new NotFoundException("Dominio non trovato: " + idDominio));
    }

    private void audit(String azione, Dominio dominio, String contentType, HttpServletRequest request) {
        OperatoreCorrente operatore = currentOperatorService.get();
        Map<String, Object> dettaglio = new HashMap<>();
        dettaglio.put("idDominio", dominio.getCodDominio());
        if (contentType != null) {
            dettaglio.put("contentType", contentType);
        }
        auditService.registra(azione, dominio.getId(), dettaglio, operatore, request);
    }

    private static String supportedTypesLabel() {
        return String.join(", ", LogoMimeDetector.supportedTypes());
    }
}
