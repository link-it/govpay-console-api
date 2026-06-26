package it.govpay.console.pendenza;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di `?sort=` per `GET /pendenze`. Formato `field[,field]*` dove ogni
 * `field` puo' essere prefissato da `-` (DESC) o `+` (ASC, default).
 * I nomi sono whitelist-ed: campi non riconosciuti causano
 * {@link IllegalArgumentException}, che il controller mappa a 400.
 */
public final class PendenzaSortParser {

    /**
     * Campi sortable allineati a V1 ({@code ListaPendenzeDTO}: {@code dataCaricamento},
     * {@code dataValidita}, {@code dataScadenza}, {@code stato}) piu'
     * {@code dataUltimoAggiornamento} aggiunto come default dall'issue #9.
     * Le chiavi della map sono i nomi pubblici (query param), i valori i nomi
     * dei campi entity JPA per la query.
     */
    private static final Map<String, String> WHITELIST = Map.of(
            "dataUltimoAggiornamento", "dataOraUltimoAggiornamento",
            "dataCaricamento", "dataCreazione",
            "dataValidita", "dataValidita",
            "dataScadenza", "dataScadenza",
            "stato", "statoVersamento");

    public static final String DEFAULT_SORT_RAW = "-dataUltimoAggiornamento";

    private PendenzaSortParser() {
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
