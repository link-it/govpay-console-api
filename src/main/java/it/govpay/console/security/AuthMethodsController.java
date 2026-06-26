package it.govpay.console.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.console.model.MetodiAutenticazione;

import java.time.Duration;

/**
 * Endpoint pubblico di discovery dei metodi di autenticazione attivi. Il
 * frontend lo chiama prima della pagina di login per scegliere quali
 * pulsanti renderizzare.
 *
 * <p>Cache-Control {@code public, max-age=300}: l'output dipende solo
 * dalla configurazione server; uno short TTL evita round-trip ridondanti
 * sui reload del frontend.
 */
@RestController
public class AuthMethodsController {

    private final MetodiAutenticazioneResolver resolver;

    public AuthMethodsController(MetodiAutenticazioneResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/auth/methods")
    public ResponseEntity<MetodiAutenticazione> getMethods() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(resolver.resolve());
    }
}
