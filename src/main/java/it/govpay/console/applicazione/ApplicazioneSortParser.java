package it.govpay.console.applicazione;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /applicazioni`. Formato `field[,field]*` dove ogni
 * `field` puo' essere prefissato da `-` (DESC) o `+` (ASC, default). I nomi sono
 * whitelist-ed: campi non riconosciuti causano {@link IllegalArgumentException},
 * che il controller mappa a 400.
 */
public final class ApplicazioneSortParser {

    /**
     * Chiavi = nomi pubblici (query param), valori = path del campo entity JPA
     * (eventualmente annidato attraverso la relazione {@code utenza}).
     */
    private static final Map<String, String> WHITELIST = Map.of(
            "idA2A", "codApplicazione",
            "principal", "utenza.principalOriginale");

    public static final String DEFAULT_SORT_RAW = "idA2A";

    private ApplicazioneSortParser() {
    }

    public static Sort parse(String raw) {
        String value = (raw == null || raw.isBlank()) ? DEFAULT_SORT_RAW : raw.trim();
        List<Sort.Order> orders = new ArrayList<>();
        for (String token : value.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (t.startsWith("-")) {
                direction = Sort.Direction.DESC;
                t = t.substring(1);
            } else if (t.startsWith("+")) {
                t = t.substring(1);
            }
            String entityField = WHITELIST.get(t);
            if (entityField == null) {
                throw new IllegalArgumentException("Campo di sort non supportato: " + t
                        + ". Campi ammessi: " + WHITELIST.keySet());
            }
            orders.add(new Sort.Order(direction, entityField));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
