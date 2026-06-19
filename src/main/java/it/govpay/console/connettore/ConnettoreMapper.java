package it.govpay.console.connettore;

import static it.govpay.console.connettore.ConnettoreProprietaKeys.ABILITATO;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.ABILITA_GDE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.API_ID;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.API_KEY;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.HTTPPASSW;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.HTTPUSER;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.HTTP_HEADER_NAME;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.HTTP_HEADER_VALUE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.OAUTH2_CLIENT_ID;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.OAUTH2_CLIENT_SECRET;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.OAUTH2_SCOPE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.OAUTH2_URL_TOKEN_ENDPOINT;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLKSLOCATION;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLKSPASSWD;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLKSTYPE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLPKEYPASSWD;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLTSLOCATION;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLTSPASSWD;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLTSTYPE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SSLTYPE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.SUBSCRIPTION_KEY;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.TIPOAUTENTICAZIONE;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.TIPOSSL;
import static it.govpay.console.connettore.ConnettoreProprietaKeys.URL;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversione bidirezionale tra i DTO di canale (qualunque shape: pagopa con
 * {@code urlRPT}, o secondaria con {@code url} + {@code abilitaGDE}) e la mappa
 * {@code proprieta -> valore} della tabella EAV. Lavora su {@link JsonNode} per
 * trattare i 6 tipi DTO generati in modo uniforme. Gestisce solo le proprieta'
 * di configurazione e di credenziale; non tocca le altre.
 */
@Component
public class ConnettoreMapper {

    private final ObjectMapper objectMapper;

    public ConnettoreMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** DTO di canale -> mappa delle proprieta' di configurazione (no credenziali). */
    public Map<String, String> toConfigMap(JsonNode dto, ConnettoreCanale canale) {
        Map<String, String> map = new HashMap<>();
        putBoolean(map, ABILITATO, dto.get("abilitato"));
        putString(map, URL, text(dto, canale.isPagopaShape() ? "urlRPT" : "url"));
        if (!canale.isPagopaShape()) {
            putBoolean(map, ABILITA_GDE, dto.get("abilitaGDE"));
        }
        JsonNode auth = dto.get("auth");
        if (auth != null && !auth.isNull()) {
            putString(map, TIPOAUTENTICAZIONE, authTypeToV1(text(auth, "tipoAutenticazione")));
            putString(map, TIPOSSL, text(auth, "sslTipo"));
            putString(map, HTTPUSER, text(auth, "username"));
            putString(map, SSLKSLOCATION, text(auth, "ksLocation"));
            putString(map, SSLKSTYPE, text(auth, "ksType"));
            putString(map, SSLTSLOCATION, text(auth, "tsLocation"));
            putString(map, SSLTSTYPE, text(auth, "tsType"));
            putString(map, SSLTYPE, text(auth, "sslType"));
            putString(map, HTTP_HEADER_NAME, text(auth, "headerName"));
            putString(map, API_ID, text(auth, "apiId"));
            putString(map, OAUTH2_CLIENT_ID, text(auth, "clientId"));
            putString(map, OAUTH2_SCOPE, text(auth, "scope"));
            putString(map, OAUTH2_URL_TOKEN_ENDPOINT, text(auth, "urlTokenEndpoint"));
        }
        return map;
    }

    /** Mappa delle proprieta' -> DTO di canale (credenziali escluse). */
    public ObjectNode toDtoNode(Map<String, String> config, ConnettoreCanale canale) {
        ObjectNode dto = objectMapper.createObjectNode();
        dto.put("abilitato", parseBoolean(config.get(ABILITATO)));
        dto.put(canale.isPagopaShape() ? "urlRPT" : "url", config.get(URL));
        if (!canale.isPagopaShape()) {
            dto.put("abilitaGDE", parseBoolean(config.get(ABILITA_GDE)));
        }
        ObjectNode auth = dto.putObject("auth");
        String tipo = authTypeToV2(config.get(TIPOAUTENTICAZIONE));
        auth.put("tipoAutenticazione", tipo != null ? tipo : "NONE");
        auth.put("sslTipo", config.get(TIPOSSL));
        auth.put("username", config.get(HTTPUSER));
        auth.put("ksLocation", config.get(SSLKSLOCATION));
        auth.put("ksType", config.get(SSLKSTYPE));
        auth.put("tsLocation", config.get(SSLTSLOCATION));
        auth.put("tsType", config.get(SSLTSTYPE));
        auth.put("sslType", config.get(SSLTYPE));
        auth.put("headerName", config.get(HTTP_HEADER_NAME));
        auth.put("apiId", config.get(API_ID));
        auth.put("clientId", config.get(OAUTH2_CLIENT_ID));
        auth.put("scope", config.get(OAUTH2_SCOPE));
        auth.put("urlTokenEndpoint", config.get(OAUTH2_URL_TOKEN_ENDPOINT));
        return dto;
    }

    /** DTO credenziali -> mappa delle proprieta' di credenziale. */
    public Map<String, String> toCredenzialiMap(JsonNode creds) {
        Map<String, String> map = new HashMap<>();
        putString(map, SUBSCRIPTION_KEY, text(creds, "subscriptionKey"));
        putString(map, HTTPPASSW, text(creds, "password"));
        putString(map, SSLKSPASSWD, text(creds, "ksPassword"));
        putString(map, SSLPKEYPASSWD, text(creds, "ksPKeyPasswd"));
        putString(map, SSLTSPASSWD, text(creds, "tsPassword"));
        putString(map, HTTP_HEADER_VALUE, text(creds, "headerValue"));
        putString(map, API_KEY, text(creds, "apiKey"));
        putString(map, OAUTH2_CLIENT_SECRET, text(creds, "clientSecret"));
        return map;
    }

    private static String authTypeToV1(String v2) {
        if (v2 == null) {
            return null;
        }
        return switch (v2) {
            case "NONE" -> "NONE";
            case "HTTPBASIC" -> "HTTPBasic";
            case "SSL" -> "SSL";
            case "HEADER" -> "HTTP_HEADER";
            case "APIKEY" -> "API_KEY";
            case "OAUTH2" -> "OAUTH2_CLIENT_CREDENTIALS";
            default -> null;
        };
    }

    private static String authTypeToV2(String v1) {
        if (v1 == null) {
            return null;
        }
        return switch (v1) {
            case "NONE" -> "NONE";
            case "HTTPBasic" -> "HTTPBASIC";
            case "SSL" -> "SSL";
            case "HTTP_HEADER" -> "HEADER";
            case "API_KEY" -> "APIKEY";
            case "OAUTH2_CLIENT_CREDENTIALS" -> "OAUTH2";
            default -> null;
        };
    }

    private static void putString(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private static void putBoolean(Map<String, String> map, String key, JsonNode value) {
        if (value != null && !value.isNull()) {
            map.put(key, Boolean.toString(value.asBoolean()));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
