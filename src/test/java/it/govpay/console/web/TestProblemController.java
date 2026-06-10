package it.govpay.console.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Controller di test usato solo dai test di {@link ProblemExceptionHandler}.
 * Esposto sotto `/_test-problem/*`. Va importato esplicitamente dai test
 * (non c'e' component-scan da `src/test/java`).
 */
@RestController
@RequestMapping("/_test-problem")
public class TestProblemController {

    @PostMapping("/validation")
    public ResponseEntity<Void> validation(@Valid @RequestBody Payload payload) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/not-found")
    public void notFound() {
        throw new NotFoundException("Risorsa non trovata.");
    }

    @GetMapping("/optimistic-lock")
    public void optimisticLock() {
        throw new IfMatchMismatchException("ETag non corrispondente.");
    }

    @GetMapping("/integrity")
    public void integrity() {
        throw new DataIntegrityViolationException("vincolo violato");
    }

    @GetMapping("/generic")
    public void generic() {
        throw new IllegalStateException("dettaglio interno che non deve uscire");
    }

    public static class Payload {
        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
