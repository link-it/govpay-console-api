package it.govpay.console.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /domini`. Formato `field[,field]*` con prefisso
 * `-` (DESC) o `+`/nessuno (ASC). Campi non in whitelist causano
 * {@link IllegalArgumentException}, mappata a 400 dal controller.
 */
public final class DominioSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "idDominio", "codDominio",
            "ragioneSociale", "ragioneSociale");

    public static final String DEFAULT_SORT_RAW = "idDominio";

    private DominioSortParser() {
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
