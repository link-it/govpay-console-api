package it.govpay.console.entrata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /entrate`. Formato `field[,field]*` dove ogni
 * `field` puo' essere prefissato da `-` (DESC) o `+` (ASC, default). I nomi sono
 * whitelist-ed: campi non riconosciuti causano {@link IllegalArgumentException},
 * che il controller mappa a 400.
 */
public final class EntrataSortParser {

    /**
     * Chiavi = nomi pubblici (query param), valori = nomi dei campi entity JPA.
     */
    private static final Map<String, String> WHITELIST = Map.of(
            "idEntrata", "codTributo",
            "descrizione", "descrizione");

    public static final String DEFAULT_SORT_RAW = "idEntrata";

    private EntrataSortParser() {
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
