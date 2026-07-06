package it.govpay.console.operatore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /operatori`. Campi ammessi: `principal`
 * (→ {@code utenza.principalOriginale}) e `nome`. Prefisso `-` per DESC.
 */
public final class OperatoreSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "principal", "utenza.principalOriginale",
            "nome", "nome");

    public static final String DEFAULT_SORT_RAW = "principal";

    private OperatoreSortParser() {
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
