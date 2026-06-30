package it.govpay.console.entratadominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /domini/{id}/entrate`. I campi esposti sono mappati
 * su path entita' annidati (l'identita' e la descrizione vivono sull'entrata
 * globale {@code tipoTributo}). Nomi non riconosciuti causano
 * {@link IllegalArgumentException} (400 dal controller).
 */
public final class EntrataDominioSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "idEntrata", "tipoTributo.codTributo",
            "descrizione", "tipoTributo.descrizione");

    public static final String DEFAULT_SORT_RAW = "idEntrata";

    private EntrataDominioSortParser() {
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
