package org.openspcoop2.utils.json;

import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Classe di compatibilità per i template FreeMarker esistenti.
 * Replica le funzionalità di org.openspcoop2.utils.json.JSONUtils di GovWay.
 */
public class JSONUtils {

    private static final JSONUtils instance = new JSONUtils(false);
    private static final JSONUtils instancePretty = new JSONUtils(true);

    private final ObjectMapper objectMapper;
    private final boolean prettyPrint;

    private JSONUtils(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        this.objectMapper = new ObjectMapper();
    }

    public static JSONUtils getInstance() {
        return instance;
    }

    public static JSONUtils getInstance(boolean prettyPrint) {
        return prettyPrint ? instancePretty : instance;
    }

    /**
     * Converte una stringa JSON in un JsonNode.
     */
    public JsonNode getAsNode(String jsonString) throws Exception {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        return objectMapper.readTree(jsonString);
    }

    /**
     * Converte un array di byte JSON in un JsonNode.
     */
    public JsonNode getAsNode(byte[] jsonBytes) throws Exception {
        if (jsonBytes == null || jsonBytes.length == 0) {
            return null;
        }
        return objectMapper.readTree(jsonBytes);
    }

    /**
     * Converte un InputStream JSON in un JsonNode.
     */
    public JsonNode getAsNode(InputStream jsonStream) throws Exception {
        if (jsonStream == null) {
            return null;
        }
        return objectMapper.readTree(jsonStream);
    }

    /**
     * Deserializza una stringa JSON in un oggetto della classe specificata.
     */
    public <T> T getAsObject(String jsonString, Class<T> clazz) throws Exception {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        return objectMapper.readValue(jsonString, clazz);
    }

    /**
     * Deserializza un array di byte JSON in un oggetto della classe specificata.
     */
    public <T> T getAsObject(byte[] jsonBytes, Class<T> clazz) throws Exception {
        if (jsonBytes == null || jsonBytes.length == 0) {
            return null;
        }
        return objectMapper.readValue(jsonBytes, clazz);
    }

    /**
     * Crea un nuovo ObjectNode vuoto.
     */
    public ObjectNode newObjectNode() {
        return objectMapper.createObjectNode();
    }

    /**
     * Crea un nuovo ArrayNode vuoto.
     */
    public ArrayNode newArrayNode() {
        return objectMapper.createArrayNode();
    }

    /**
     * Converte un JsonNode in stringa JSON.
     */
    public String toString(JsonNode node) throws Exception {
        if (node == null) {
            return null;
        }
        if (prettyPrint) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        }
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Converte un JsonNode in array di byte.
     */
    public byte[] toByteArray(JsonNode node) throws Exception {
        if (node == null) {
            return null;
        }
        if (prettyPrint) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        }
        return objectMapper.writeValueAsBytes(node);
    }

    /**
     * Verifica se una stringa è un JSON valido.
     */
    public boolean isJson(String jsonString) {
        try {
            getAsNode(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica se un array di byte è un JSON valido.
     */
    public boolean isJson(byte[] jsonBytes) {
        try {
            getAsNode(jsonBytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Restituisce l'ObjectMapper interno.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
