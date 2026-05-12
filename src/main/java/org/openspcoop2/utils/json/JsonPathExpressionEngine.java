package org.openspcoop2.utils.json;

import com.jayway.jsonpath.JsonPath;

/**
 * Classe di compatibilità per i template FreeMarker esistenti.
 * Fornisce un wrapper per le operazioni JsonPath.
 */
public class JsonPathExpressionEngine {

    private final String jsonContent;

    public JsonPathExpressionEngine(String jsonContent) {
        this.jsonContent = jsonContent;
    }

    public JsonPathExpressionEngine(byte[] jsonBytes) {
        this.jsonContent = jsonBytes != null ? new String(jsonBytes) : null;
    }

    /**
     * Legge un valore dal JSON usando un'espressione JsonPath.
     * Il pattern "$" restituisce l'intero documento.
     *
     * @param pattern l'espressione JsonPath (es. "$", "$.campo", "$.array[0]")
     * @return il risultato come stringa JSON
     */
    public String read(String pattern) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return null;
        }

        Object result = JsonPath.read(jsonContent, pattern);

        if (result == null) {
            return null;
        }

        // Se è già una stringa semplice, la restituiamo
        if (result instanceof String) {
            return (String) result;
        }

        // Altrimenti convertiamo in JSON string
        try {
            return JSONUtils.getInstance().getObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    /**
     * Legge un valore dal JSON e lo restituisce come oggetto tipizzato.
     */
    @SuppressWarnings("unchecked")
    public <T> T readAs(String pattern, Class<T> clazz) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            return null;
        }
        return JsonPath.read(jsonContent, pattern);
    }

    /**
     * Crea un'istanza con il contenuto JSON fornito.
     */
    public static JsonPathExpressionEngine getInstance(String jsonContent) {
        return new JsonPathExpressionEngine(jsonContent);
    }

    /**
     * Crea un'istanza con il contenuto JSON fornito.
     */
    public static JsonPathExpressionEngine getInstance(byte[] jsonBytes) {
        return new JsonPathExpressionEngine(jsonBytes);
    }
}
