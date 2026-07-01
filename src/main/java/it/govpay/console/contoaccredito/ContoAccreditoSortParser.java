package it.govpay.console.contoaccredito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /domini/{id}/contiAccredito`. Formato `field[,field]*`,
 * ogni `field` prefissabile da `-` (DESC) o `+` (ASC, default). I nomi esposti sono
 * mappati sui campi entita'; nomi non riconosciuti causano {@link IllegalArgumentException}
 * (400 dal controller).
 */
public final class ContoAccreditoSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "ibanAccredito", "codIban",
            "descrizione", "descrizione");

    public static final String DEFAULT_SORT_RAW = "ibanAccredito";

    private ContoAccreditoSortParser() {
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
