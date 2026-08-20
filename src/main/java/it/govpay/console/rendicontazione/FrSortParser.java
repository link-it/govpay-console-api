package it.govpay.console.rendicontazione;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;

/**
 * Parser di {@code ?sort=} per {@code GET /flussi-rendicontazione}. Stesso
 * formato di {@link it.govpay.console.ricevuta.RicevutaSortParser}:
 * {@code field[,field]*} con prefisso {@code -} (DESC) o {@code +}/assente (ASC).
 *
 * <p>Il default (nessun {@code ?sort=}) applica l'ordinamento a 3 chiavi
 * documentato dalla issue ({@code dataOraFlusso DESC, idFlusso ASC, revisione
 * DESC}) per determinismo in modalità offset; {@code idFlusso} non è però
 * esplicitamente selezionabile dal client (non è nella whitelist pubblica).
 */
public final class FrSortParser {

    private static final Map<String, String> WHITELIST = Map.of(
            "dataOraFlusso", "dataOraFlusso",
            "dataAcquisizione", "dataAcquisizione",
            "dataRegolamento", "dataRegolamento",
            "importoTotale", "importoTotalePagamenti",
            "numeroPagamenti", "numeroPagamenti",
            "revisione", "revisione");

    public static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("dataOraFlusso"),
            Sort.Order.asc("codFlusso"),
            Sort.Order.desc("revisione"));

    private FrSortParser() {
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
