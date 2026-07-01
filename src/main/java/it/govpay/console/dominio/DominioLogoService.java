package it.govpay.console.dominio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.console.audit.AuditService;
import it.govpay.console.entity.Dominio;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.security.CurrentOperatorService;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.web.NotFoundException;
import it.govpay.console.web.PayloadTooLargeException;
import it.govpay.console.web.UnsupportedMediaTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class DominioLogoService {

    public static final String AZIONE_AUDIT_MODIFICA = "DOMINIO_LOGO_MODIFICA";
    public static final String AZIONE_AUDIT_RIMUOVI = "DOMINIO_LOGO_RIMUOVI";

    private final DominioRepository repository;
    private final CurrentOperatorService currentOperatorService;
    private final AuditService auditService;
    private final long maxSizeBytes;
    private final long cacheMaxAgeSeconds;

    public DominioLogoService(DominioRepository repository,
                              CurrentOperatorService currentOperatorService,
                              AuditService auditService,
                              @Value("${app.dominio.logo.max-size-bytes:262144}") long maxSizeBytes,
                              @Value("${app.dominio.logo.cache-max-age-seconds:86400}") long cacheMaxAgeSeconds) {
        this.repository = repository;
        this.currentOperatorService = currentOperatorService;
        this.auditService = auditService;
        this.maxSizeBytes = maxSizeBytes;
        this.cacheMaxAgeSeconds = cacheMaxAgeSeconds;
    }

    @Transactional(readOnly = true)
    public void getLogo(String idDominio, HttpServletResponse response) {
        Dominio dominio = load(idDominio);
        byte[] stored = dominio.getLogo();
        if (stored == null || stored.length == 0) {
            throw new NotFoundException("Il dominio '" + idDominio + "' non ha un logo.");
        }
        byte[] image = DominioLogoCodec.decode(stored);
        String contentType = LogoMimeDetector.detect(image);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        response.setContentType(contentType);
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

        dominio.setLogo(DominioLogoCodec.encode(content));
        repository.save(dominio);

        audit(AZIONE_AUDIT_MODIFICA, dominio, contentType, request);
    }

    @Transactional
    public void deleteLogo(String idDominio, HttpServletRequest request) {
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
