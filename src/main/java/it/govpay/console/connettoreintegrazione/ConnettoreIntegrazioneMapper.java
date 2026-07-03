package it.govpay.console.connettoreintegrazione;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import it.govpay.console.connettore.ConnettoreProprietaKeys;
import it.govpay.console.model.ConnettoreCredenziali;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione.TipoAutenticazioneEnum;
import it.govpay.console.model.ConnettoreIntegrazioneApplicazione.VersioneEnum;
import it.govpay.console.model.SslTipo;

/**
 * Traduce fra la rappresentazione API {@link ConnettoreIntegrazioneApplicazione}
 * e le proprieta' EAV del connettore ({@code connettori}). I valori memorizzati
 * ricalcano il formato del core V1: {@code VERSIONE} = {@code REST_1}/{@code REST_2}
 * (apiLabel), {@code TIPOAUTENTICAZIONE} = nome di {@code EnumAuthType}
 * ({@code NONE}/{@code HTTPBasic}/{@code SSL}), {@code ABILITATO} = {@code true}/{@code false}.
 */
@Component
public class ConnettoreIntegrazioneMapper {

    /** Chiave EAV della versione (non gestita dallo store dei connettori pagoPA). */
    static final String VERSIONE = "VERSIONE";
    /** Chiavi EAV dei timeout: introdotte in V2, non lette dal core V1. */
    static final String CONNECTION_TIMEOUT = "CONNECTION_TIMEOUT";
    static final String READ_TIMEOUT = "READ_TIMEOUT";

    // Valori EAV di TIPOAUTENTICAZIONE (nomi di it.govpay.model.Connettore.EnumAuthType).
    private static final String AUTH_NONE = "NONE";
    private static final String AUTH_BASIC = "HTTPBasic";
    private static final String AUTH_SSL = "SSL";

    // Valori EAV di VERSIONE (apiLabel di it.govpay.model.Versionabile.Versione).
    private static final String VERSIONE_REST_1 = "REST_1";
    private static final String VERSIONE_REST_2 = "REST_2";

    public static final Set<String> CONFIG_KEYS = Set.of(
            ConnettoreProprietaKeys.ABILITATO,
            ConnettoreProprietaKeys.URL,
            VERSIONE,
            ConnettoreProprietaKeys.TIPOAUTENTICAZIONE,
            ConnettoreProprietaKeys.HTTPUSER,
            ConnettoreProprietaKeys.TIPOSSL,
            ConnettoreProprietaKeys.SSLKSLOCATION,
            ConnettoreProprietaKeys.SSLKSTYPE,
            ConnettoreProprietaKeys.SSLTSLOCATION,
            ConnettoreProprietaKeys.SSLTSTYPE,
            ConnettoreProprietaKeys.SSLTYPE,
            CONNECTION_TIMEOUT,
            READ_TIMEOUT);

    public static final Set<String> CREDENTIAL_KEYS = Set.of(
            ConnettoreProprietaKeys.HTTPPASSW,
            ConnettoreProprietaKeys.SSLKSPASSWD,
            ConnettoreProprietaKeys.SSLPKEYPASSWD,
            ConnettoreProprietaKeys.SSLTSPASSWD);

    public ConnettoreIntegrazioneApplicazione toDto(Map<String, String> config) {
        ConnettoreIntegrazioneApplicazione dto = new ConnettoreIntegrazioneApplicazione();
        dto.setAbilitato(Boolean.parseBoolean(config.get(ConnettoreProprietaKeys.ABILITATO)));
        dto.setUrl(config.get(ConnettoreProprietaKeys.URL));
        dto.setVersione(versioneFromStored(config.get(VERSIONE)));
        dto.setTipoAutenticazione(authFromStored(config.get(ConnettoreProprietaKeys.TIPOAUTENTICAZIONE)));
        dto.setUsername(config.get(ConnettoreProprietaKeys.HTTPUSER));
        dto.setSslTipo(sslTipoFromStored(config.get(ConnettoreProprietaKeys.TIPOSSL)));
        dto.setKsLocation(config.get(ConnettoreProprietaKeys.SSLKSLOCATION));
        dto.setKsType(config.get(ConnettoreProprietaKeys.SSLKSTYPE));
        dto.setTsLocation(config.get(ConnettoreProprietaKeys.SSLTSLOCATION));
        dto.setTsType(config.get(ConnettoreProprietaKeys.SSLTSTYPE));
        dto.setSslType(config.get(ConnettoreProprietaKeys.SSLTYPE));
        dto.setConnectTimeoutMs(parseIntOrNull(config.get(CONNECTION_TIMEOUT)));
        dto.setReadTimeoutMs(parseIntOrNull(config.get(READ_TIMEOUT)));
        // Le password (HTTP Basic e keystore/truststore) non vengono mai esposte.
        return dto;
    }

    public Map<String, String> toConfigMap(ConnettoreIntegrazioneApplicazione dto) {
        Map<String, String> map = new HashMap<>();
        map.put(ConnettoreProprietaKeys.ABILITATO, String.valueOf(Boolean.TRUE.equals(dto.getAbilitato())));
        putIfNotBlank(map, ConnettoreProprietaKeys.URL, dto.getUrl());
        if (dto.getVersione() != null) {
            map.put(VERSIONE, versioneToStored(dto.getVersione()));
        }
        if (dto.getTipoAutenticazione() != null) {
            map.put(ConnettoreProprietaKeys.TIPOAUTENTICAZIONE, authToStored(dto.getTipoAutenticazione()));
        }
        putIfNotBlank(map, ConnettoreProprietaKeys.HTTPUSER, dto.getUsername());
        if (dto.getSslTipo() != null) {
            map.put(ConnettoreProprietaKeys.TIPOSSL, dto.getSslTipo().getValue());
        }
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLKSLOCATION, dto.getKsLocation());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLKSTYPE, dto.getKsType());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTSLOCATION, dto.getTsLocation());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTSTYPE, dto.getTsType());
        putIfNotBlank(map, ConnettoreProprietaKeys.SSLTYPE, dto.getSslType());
        if (dto.getConnectTimeoutMs() != null) {
            map.put(CONNECTION_TIMEOUT, String.valueOf(dto.getConnectTimeoutMs()));
        }
        if (dto.getReadTimeoutMs() != null) {
            map.put(READ_TIMEOUT, String.valueOf(dto.getReadTimeoutMs()));
        }
        return map;
    }

    public Map<String, String> toCredenzialiMap(ConnettoreCredenziali credenziali) {
        Map<String, String> map = new HashMap<>();
        map.put(ConnettoreProprietaKeys.HTTPPASSW, credenziali.getPassword());
        map.put(ConnettoreProprietaKeys.SSLKSPASSWD, credenziali.getKsPassword());
        map.put(ConnettoreProprietaKeys.SSLPKEYPASSWD, credenziali.getKsPKeyPasswd());
        map.put(ConnettoreProprietaKeys.SSLTSPASSWD, credenziali.getTsPassword());
        return map;
    }

    private static VersioneEnum versioneFromStored(String stored) {
        if (stored == null) {
            return null;
        }
        return switch (stored) {
            case VERSIONE_REST_1 -> VersioneEnum.REST_V1;
            case VERSIONE_REST_2 -> VersioneEnum.REST_V2;
            default -> null;
        };
    }

    private static String versioneToStored(VersioneEnum versione) {
        return versione == VersioneEnum.REST_V2 ? VERSIONE_REST_2 : VERSIONE_REST_1;
    }

    private static TipoAutenticazioneEnum authFromStored(String stored) {
        if (stored == null) {
            return null;
        }
        return switch (stored) {
            case AUTH_BASIC -> TipoAutenticazioneEnum.BASIC;
            case AUTH_SSL -> TipoAutenticazioneEnum.SSL;
            case AUTH_NONE -> TipoAutenticazioneEnum.NONE;
            default -> null;
        };
    }

    private static String authToStored(TipoAutenticazioneEnum tipo) {
        return switch (tipo) {
            case BASIC -> AUTH_BASIC;
            case SSL -> AUTH_SSL;
            case NONE -> AUTH_NONE;
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

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
