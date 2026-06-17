package it.govpay.console.profilo;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.auth.AuthTypeAccessor;
import it.govpay.common.auth.spi.AuthType;
import it.govpay.console.model.AutenticazioneEnum;
import it.govpay.console.model.Profilo;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;

/**
 * Endpoint {@code GET /profilo}: ritorna il {@link Profilo} dell'utenza
 * autenticata corrente.
 *
 * <p>{@code Profilo.autenticazione} viene derivato via
 * {@link AuthTypeAccessor} dal marker stampato dall'{@code AuthTypeStampingFilter}.
 * Se la request arriva qui, il {@code SecurityContext} e' gia' stato
 * valorizzato dalla chain di sicurezza.
 *
 * <p>Cache-Control: {@code private, max-age=60}. Dato personale (niente
 * caching condiviso); short TTL per ridurre carico DB sulle UI che lo
 * rinfrescano spesso (sidebar profilo).
 */
@RestController
@RequestMapping
public class ProfiloController {

    private final ProfiloService profiloService;

    public ProfiloController(ProfiloService profiloService) {
        this.profiloService = profiloService;
    }

    @GetMapping("/profilo")
    public ResponseEntity<Profilo> getProfilo(HttpServletRequest request) {
        Profilo profilo = profiloService.build();
        AuthType authType = AuthTypeAccessor.current(request);
        if (authType != null) {
            profilo.setAutenticazione(AutenticazioneEnum.fromValue(authType.name()));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePrivate())
                .body(profilo);
    }
}
