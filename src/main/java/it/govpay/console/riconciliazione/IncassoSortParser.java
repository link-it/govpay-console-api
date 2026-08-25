package it.govpay.console.riconciliazione;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di {@code ?sort=} per {@code GET /riconciliazioni}. Stesso formato di
 * {@link it.govpay.console.rendicontazione.FrSortParser}: {@code field[,field]*}
 * con prefisso {@code -} (DESC) o {@code +}/assente (ASC).
 */
public final class IncassoSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "data", "dataOraIncasso",
            "importo", "importo");

    public static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("dataOraIncasso"),
            Sort.Order.desc("id"));

    private IncassoSortParser() {
    }

    public static Sort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SORT;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String token : raw.split(",")) {
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
        return orders.isEmpty() ? DEFAULT_SORT : Sort.by(orders);
    }
}
