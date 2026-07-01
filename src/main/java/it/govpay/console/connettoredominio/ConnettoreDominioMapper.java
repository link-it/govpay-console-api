package it.govpay.console.connettoredominio;

import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.ABILITATO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.API_ID;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.CODICE_CLIENTE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.CODICE_IPA;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.CODICE_ISTITUTO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.CONTENUTI;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.DOWNLOAD_BASE_URL;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.EMAIL_ALLEGATO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.EMAIL_INDIRIZZO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.EMAIL_SUBJECT;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.FILE_SYSTEM_PATH;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.HTTPUSER;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.HTTP_HEADER_NAME;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.INTERVALLO_CREAZIONE_TRACCIATO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.INVIA_TRACCIATO_ESITO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.OAUTH2_CLIENT_ID;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.OAUTH2_SCOPE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.OAUTH2_URL_TOKEN_ENDPOINT;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.SSLKSLOCATION;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.SSLKSTYPE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.SSLTSLOCATION;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.SSLTSTYPE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.SSLTYPE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.TIPOAUTENTICAZIONE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.TIPOSSL;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.TIPO_CONNETTORE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.TIPO_TRACCIATO;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.URL;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.VERSIONE;
import static it.govpay.console.connettoredominio.ConnettoreDominioProprietaKeys.VERSIONE_CSV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.connettore.ConnettoreProprietaKeys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversione bidirezionale tra i 5 DTO di canale (shape distinte) e la mappa
 * {@code proprieta -> valore} della tabella EAV {@code connettori}, fedele alla
 * codifica storica di GovPay: array serializzati come CSV, {@code tipoConnettore}
 * come {@code EMAIL/FILE_SYSTEM/REST}, {@code versioneApi "REST v1"} come
 * {@code REST_1}. L'auth (canali GovPay e Maggioli JPPA) riusa lo stesso schema
 * dei connettori intermediario.
 */
@Component
public class ConnettoreDominioMapper {

    private enum Kind { STRING, BOOL, INT, CSV, TIPO_CONNETTORE_ENUM, VERSIONE_API }

    private record FieldSpec(String dtoField, String key, Kind kind) {
    }

    private final ObjectMapper objectMapper;

    public ConnettoreDominioMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** DTO di canale -> mappa delle proprieta' di configurazione (no credenziali). */
    public Map<String, String> toConfigMap(JsonNode dto, ConnettoreDominioCanale canale) {
        Map<String, String> map = new HashMap<>();
        map.put(TIPO_TRACCIATO, canale.tipo());
        for (FieldSpec f : fields(canale)) {
            writeField(dto, f, map);
        }
        if (canale.hasCredenziali()) {
            writeAuth(dto.get("auth"), map);
        }
        return map;
    }

    /** Mappa delle proprieta' -> DTO di canale (credenziali escluse). */
    public ObjectNode toDtoNode(Map<String, String> config, ConnettoreDominioCanale canale) {
        ObjectNode dto = objectMapper.createObjectNode();
        for (FieldSpec f : fields(canale)) {
            readField(config, f, dto);
        }
        if (canale.hasCredenziali()) {
            dto.set("auth", readAuth(config));
        }
        return dto;
    }

    /** DTO credenziali -> mappa delle proprieta' di credenziale. */
    public Map<String, String> toCredenzialiMap(JsonNode creds) {
        Map<String, String> map = new HashMap<>();
        putString(map, ConnettoreProprietaKeys.SUBSCRIPTION_KEY, text(creds, "subscriptionKey"));
        putString(map, ConnettoreProprietaKeys.HTTPPASSW, text(creds, "password"));
        putString(map, ConnettoreProprietaKeys.SSLKSPASSWD, text(creds, "ksPassword"));
        putString(map, ConnettoreProprietaKeys.SSLPKEYPASSWD, text(creds, "ksPKeyPasswd"));
        putString(map, ConnettoreProprietaKeys.SSLTSPASSWD, text(creds, "tsPassword"));
        putString(map, ConnettoreProprietaKeys.HTTP_HEADER_VALUE, text(creds, "headerValue"));
        putString(map, ConnettoreProprietaKeys.API_KEY, text(creds, "apiKey"));
        putString(map, ConnettoreProprietaKeys.OAUTH2_CLIENT_SECRET, text(creds, "clientSecret"));
        return map;
    }

    // --- field descriptors per canale ---

    private static final List<FieldSpec> BASE_CSV = List.of(
            new FieldSpec("abilitato", ABILITATO, Kind.BOOL),
            new FieldSpec("tipoConnettore", TIPO_CONNETTORE, Kind.TIPO_CONNETTORE_ENUM),
            new FieldSpec("versioneCsv", VERSIONE_CSV, Kind.STRING),
            new FieldSpec("emailIndirizzi", EMAIL_INDIRIZZO, Kind.CSV),
            new FieldSpec("emailSubject", EMAIL_SUBJECT, Kind.STRING),
            new FieldSpec("emailAllegato", EMAIL_ALLEGATO, Kind.BOOL),
            new FieldSpec("downloadBaseUrl", DOWNLOAD_BASE_URL, Kind.STRING),
            new FieldSpec("fileSystemPath", FILE_SYSTEM_PATH, Kind.STRING),
            new FieldSpec("tipiPendenza", ConnettoreDominioProprietaKeys.TIPI_PENDENZA, Kind.CSV),
            new FieldSpec("intervalloCreazioneTracciato", INTERVALLO_CREAZIONE_TRACCIATO, Kind.INT));

    private List<FieldSpec> fields(ConnettoreDominioCanale canale) {
        return switch (canale) {
            case MYPIVOT -> concat(List.of(new FieldSpec("codiceIPA", CODICE_IPA, Kind.STRING)), BASE_CSV);
            case SECIM -> concat(List.of(
                    new FieldSpec("codiceCliente", CODICE_CLIENTE, Kind.STRING),
                    new FieldSpec("codiceIstituto", CODICE_ISTITUTO, Kind.STRING)), BASE_CSV);
            case HYPER_SIC_APKAPPA -> BASE_CSV;
            case GOVPAY -> List.of(
                    new FieldSpec("abilitato", ABILITATO, Kind.BOOL),
                    new FieldSpec("tipoConnettore", TIPO_CONNETTORE, Kind.TIPO_CONNETTORE_ENUM),
                    new FieldSpec("versioneZip", VERSIONE_CSV, Kind.STRING),
                    new FieldSpec("emailIndirizzi", EMAIL_INDIRIZZO, Kind.CSV),
                    new FieldSpec("emailSubject", EMAIL_SUBJECT, Kind.STRING),
                    new FieldSpec("emailAllegato", EMAIL_ALLEGATO, Kind.BOOL),
                    new FieldSpec("downloadBaseUrl", DOWNLOAD_BASE_URL, Kind.STRING),
                    new FieldSpec("fileSystemPath", FILE_SYSTEM_PATH, Kind.STRING),
                    new FieldSpec("tipiPendenza", ConnettoreDominioProprietaKeys.TIPI_PENDENZA, Kind.CSV),
                    new FieldSpec("url", URL, Kind.STRING),
                    new FieldSpec("versioneApi", VERSIONE, Kind.VERSIONE_API),
                    new FieldSpec("contenuti", CONTENUTI, Kind.CSV),
                    new FieldSpec("intervalloCreazioneTracciato", INTERVALLO_CREAZIONE_TRACCIATO, Kind.INT));
            case MAGGIOLI_JPPA -> List.of(
                    new FieldSpec("abilitato", ABILITATO, Kind.BOOL),
                    new FieldSpec("inviaTracciatoEsito", INVIA_TRACCIATO_ESITO, Kind.BOOL),
                    new FieldSpec("fileSystemPath", FILE_SYSTEM_PATH, Kind.STRING),
                    new FieldSpec("emailIndirizzi", EMAIL_INDIRIZZO, Kind.CSV),
                    new FieldSpec("emailSubject", EMAIL_SUBJECT, Kind.STRING),
                    new FieldSpec("emailAllegato", EMAIL_ALLEGATO, Kind.BOOL),
                    new FieldSpec("downloadBaseUrl", DOWNLOAD_BASE_URL, Kind.STRING),
                    new FieldSpec("url", URL, Kind.STRING));
        };
    }

    private static List<FieldSpec> concat(List<FieldSpec> head, List<FieldSpec> tail) {
        List<FieldSpec> out = new ArrayList<>(head);
        out.addAll(tail);
        return out;
    }

    // --- write DTO field -> config map ---

    private void writeField(JsonNode dto, FieldSpec f, Map<String, String> map) {
        JsonNode value = dto.get(f.dtoField());
        if (value == null || value.isNull()) {
            return;
        }
        switch (f.kind()) {
            case STRING -> putString(map, f.key(), value.asString());
            case BOOL -> map.put(f.key(), Boolean.toString(value.asBoolean()));
            case INT -> map.put(f.key(), Integer.toString(value.asInt()));
            case CSV -> putString(map, f.key(), joinCsv(value));
            case TIPO_CONNETTORE_ENUM -> putString(map, f.key(), tipoConnettoreToV1(value.asString()));
            case VERSIONE_API -> putString(map, f.key(), versioneApiToV1(value.asString()));
        }
    }

    private void readField(Map<String, String> config, FieldSpec f, ObjectNode dto) {
        String value = config.get(f.key());
        switch (f.kind()) {
            case STRING -> dto.put(f.dtoField(), value);
            case BOOL -> dto.put(f.dtoField(), parseBoolean(value));
            case INT -> {
                if (value != null && !value.isBlank()) {
                    dto.put(f.dtoField(), Integer.parseInt(value.trim()));
                }
            }
            case CSV -> dto.set(f.dtoField(), splitCsv(value));
            case TIPO_CONNETTORE_ENUM -> dto.put(f.dtoField(), tipoConnettoreToV2(value));
            case VERSIONE_API -> dto.put(f.dtoField(), versioneApiToV2(value));
        }
    }

    // --- auth (identico ai connettori intermediario) ---

    private void writeAuth(JsonNode auth, Map<String, String> map) {
        if (auth == null || auth.isNull()) {
            return;
        }
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

    private ObjectNode readAuth(Map<String, String> config) {
        ObjectNode auth = objectMapper.createObjectNode();
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
        return auth;
    }

    // --- value encodings ---

    private static String tipoConnettoreToV1(String v2) {
        if (v2 == null) {
            return null;
        }
        return "FILESYSTEM".equals(v2) ? "FILE_SYSTEM" : v2;
    }

    private static String tipoConnettoreToV2(String v1) {
        if (v1 == null) {
            return null;
        }
        return "FILE_SYSTEM".equals(v1) ? "FILESYSTEM" : v1;
    }

    private static String versioneApiToV1(String v2) {
        return "REST v1".equals(v2) ? "REST_1" : v2;
    }

    private static String versioneApiToV2(String v1) {
        return "REST_1".equals(v1) ? "REST v1" : v1;
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

    // --- helpers ---

    private static String joinCsv(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asString());
        }
        return String.join(",", values);
    }

    private ArrayNode splitCsv(String value) {
        ArrayNode array = objectMapper.createArrayNode();
        if (value != null && !value.isEmpty()) {
            Arrays.stream(value.split(",")).forEach(array::add);
        }
        return array;
    }

    private static void putString(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
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
