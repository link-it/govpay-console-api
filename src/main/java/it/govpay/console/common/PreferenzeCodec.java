package it.govpay.console.common;

import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Codifica/decodifica del campo {@code operatori.preferenze} (TEXT, JSON libero)
 * verso/da {@code Map<String, Object>}. Il valore NULL in colonna si legge come
 * mappa vuota, cosi' il campo e' sempre presente nelle rappresentazioni esposte
 * anche prima della prima scrittura.
 */
public final class PreferenzeCodec {

    private PreferenzeCodec() {
    }

    public static Map<String, Object> parse(String preferenze, ObjectMapper objectMapper) {
        if (preferenze == null) {
            return Map.of();
        }
        return objectMapper.readValue(preferenze, new TypeReference<Map<String, Object>>() { });
    }

    public static String serialize(Map<String, Object> preferenze, ObjectMapper objectMapper) {
        if (preferenze == null) {
            return null;
        }
        return objectMapper.writeValueAsString(preferenze);
    }
}
