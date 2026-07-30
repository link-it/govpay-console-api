package it.govpay.console.impostazioni;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.govpay.console.connettore.ConnettoreProprietaKeys;
import it.govpay.console.model.ConnettoreAuth;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ImpostazioniServizioGDE;
import it.govpay.console.model.SslTipo;
import it.govpay.console.model.TipoAutenticazioneConnettore;

/**
 * Conversione bidirezionale tra {@link ImpostazioniServizioGDE} e le proprieta'
 * EAV del connettore ({@code connettori}), stesse chiavi e stessa codifica
 * dell'autenticazione (V1 {@code EnumAuthType}) gia' usate dai connettori
 * intermediario/dominio: cosi' il connettore scritto qui e' immediatamente
 * consumabile da {@code ConnettoreService.getRestTemplate("govpay_gde_api")}
 * di govpay-common.
 */
@Component
public class ServizioGdeMapper {

    private static final String AUTH_NONE = "NONE";
    private static final String AUTH_BASIC = "HTTPBasic";
    private static final String AUTH_SSL = "SSL";
    private static final String AUTH_HEADER = "HTTP_HEADER";
    private static final String AUTH_APIKEY = "API_KEY";
    private static final String AUTH_OAUTH2 = "OAUTH2_CLIENT_CREDENTIALS";

    public ImpostazioniServizioGDE toDto(Map<String, String> config) {
        ImpostazioniServizioGDE dto = new ImpostazioniServizioGDE();
        dto.setAbilitato(Boolean.parseBoolean(config.get(ConnettoreProprietaKeys.ABILITATO)));
        dto.setUrl(config.get(ConnettoreProprietaKeys.URL));
        dto.setAuth(readAuth(config));
        return dto;
    }

    public Map<String, String> toConfigMap(ImpostazioniServizioGDE dto) {
        Map<String, String> map = new HashMap<>();
        map.put(ConnettoreProprietaKeys.ABILITATO, String.valueOf(Boolean.TRUE.equals(dto.getAbilitato())));
        putIfNotBlank(map, ConnettoreProprietaKeys.URL, dto.getUrl());
        writeAuth(dto.getAuth(), map);
        return map;
    }

    public Map<String, String> toCredenzialiMap(ConnettoreCredenziali credenziali) {
        Map<String, String> map = new HashMap<>();
        putIfNotBlank(map, ConnettoreProprietaKeys.SUBSCRIPTION_KEY, credenziali.getSubscriptionKey());
        putIfNotBlank(map, ConnettoreProprietaKeys.HTTPPASSW, credenziali.getPassword());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLKSPASSWD, credenziali.getKsPassword());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLPKEYPASSWD, credenziali.getKsPKeyPasswd());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTSPASSWD, credenziali.getTsPassword());
        putIfNotBlank(map, ConnettoreProprietaKeys.HTTP_HEADER_VALUE, credenziali.getHeaderValue());
        putIfNotBlank(map, ConnettoreProprietaKeys.API_KEY, credenziali.getApiKey());
        putIfNotBlank(map, ConnettoreProprietaKeys.OAUTH2_CLIENT_SECRET, credenziali.getClientSecret());
        return map;
    }

    private static void writeAuth(ConnettoreAuth auth, Map<String, String> map) {
        if (auth == null) {
            return;
        }
        if (auth.getTipoAutenticazione() != null) {
            map.put(ConnettoreProprietaKeys.TIPOAUTENTICAZIONE, authTypeToStored(auth.getTipoAutenticazione()));
        }
        if (auth.getSslTipo() != null) {
            map.put(ConnettoreProprietaKeys.TIPOSSL, auth.getSslTipo().getValue());
        }
        putIfNotBlank(map, ConnettoreProprietaKeys.HTTPUSER, auth.getUsername());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLKSLOCATION, auth.getKsLocation());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLKSTYPE, auth.getKsType());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTSLOCATION, auth.getTsLocation());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTSTYPE, auth.getTsType());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTYPE, auth.getSslType());
        putIfNotBlank(map, ConnettoreProprietaKeys.HTTP_HEADER_NAME, auth.getHeaderName());
        putIfNotBlank(map, ConnettoreProprietaKeys.API_ID, auth.getApiId());
        putIfNotBlank(map, ConnettoreProprietaKeys.OAUTH2_CLIENT_ID, auth.getClientId());
        putIfNotBlank(map, ConnettoreProprietaKeys.OAUTH2_SCOPE, auth.getScope());
        putIfNotBlank(map, ConnettoreProprietaKeys.OAUTH2_URL_TOKEN_ENDPOINT, auth.getUrlTokenEndpoint());
    }

    private static ConnettoreAuth readAuth(Map<String, String> config) {
        ConnettoreAuth auth = new ConnettoreAuth();
        TipoAutenticazioneConnettore tipo = authTypeFromStored(config.get(ConnettoreProprietaKeys.TIPOAUTENTICAZIONE));
        auth.setTipoAutenticazione(tipo != null ? tipo : TipoAutenticazioneConnettore.NONE);
        auth.setSslTipo(sslTipoFromStored(config.get(ConnettoreProprietaKeys.TIPOSSL)));
        auth.setUsername(config.get(ConnettoreProprietaKeys.HTTPUSER));
        auth.setKsLocation(config.get(ConnettoreProprietaKeys.SSLKSLOCATION));
        auth.setKsType(config.get(ConnettoreProprietaKeys.SSLKSTYPE));
        auth.setTsLocation(config.get(ConnettoreProprietaKeys.SSLTSLOCATION));
        auth.setTsType(config.get(ConnettoreProprietaKeys.SSLTSTYPE));
        auth.setSslType(config.get(ConnettoreProprietaKeys.SSLTYPE));
        auth.setHeaderName(config.get(ConnettoreProprietaKeys.HTTP_HEADER_NAME));
        auth.setApiId(config.get(ConnettoreProprietaKeys.API_ID));
        auth.setClientId(config.get(ConnettoreProprietaKeys.OAUTH2_CLIENT_ID));
        auth.setScope(config.get(ConnettoreProprietaKeys.OAUTH2_SCOPE));
        auth.setUrlTokenEndpoint(config.get(ConnettoreProprietaKeys.OAUTH2_URL_TOKEN_ENDPOINT));
        return auth;
    }

    private static String authTypeToStored(TipoAutenticazioneConnettore tipo) {
        return switch (tipo) {
            case NONE -> AUTH_NONE;
            case HTTPBASIC -> AUTH_BASIC;
            case SSL -> AUTH_SSL;
            case HEADER -> AUTH_HEADER;
            case APIKEY -> AUTH_APIKEY;
            case OAUTH2 -> AUTH_OAUTH2;
        };
    }

    private static TipoAutenticazioneConnettore authTypeFromStored(String stored) {
        if (stored == null) {
            return null;
        }
        return switch (stored) {
            case AUTH_NONE -> TipoAutenticazioneConnettore.NONE;
            case AUTH_BASIC -> TipoAutenticazioneConnettore.HTTPBASIC;
            case AUTH_SSL -> TipoAutenticazioneConnettore.SSL;
            case AUTH_HEADER -> TipoAutenticazioneConnettore.HEADER;
            case AUTH_APIKEY -> TipoAutenticazioneConnettore.APIKEY;
            case AUTH_OAUTH2 -> TipoAutenticazioneConnettore.OAUTH2;
            default -> null;
        };
    }

    private static SslTipo sslTipoFromStored(String stored) {
        if (stored == null) {
            return null;
        }
        return switch (stored) {
            case "CLIENT" -> SslTipo.CLIENT;
            case "SERVER" -> SslTipo.SERVER;
            default -> null;
        };
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
