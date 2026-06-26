package it.govpay.console.connettore;

import java.util.Set;

/**
 * Chiavi {@code cod_proprieta} della tabella EAV {@code connettori}, allineate a
 * quelle storiche di GovPay (per leggere i connettori gia' scritti). Le proprieta'
 * sono partizionate in {@link #CONFIG_KEYS} (gestite dalla PUT di configurazione)
 * e {@link #CREDENTIAL_KEYS} (gestite dalla PUT {@code /credenziali}); le chiavi
 * non elencate (es. {@code VERSIONE}, {@code AZIONEINURL}) non vengono toccate.
 */
public final class ConnettoreProprietaKeys {

    private ConnettoreProprietaKeys() {
    }

    // --- config ---
    public static final String ABILITATO = "ABILITATO";
    public static final String URL = "URL";
    public static final String ABILITA_GDE = "ABILITA_GDE";
    public static final String TIPOAUTENTICAZIONE = "TIPOAUTENTICAZIONE";
    public static final String TIPOSSL = "TIPOSSL";
    public static final String HTTPUSER = "HTTPUSER";
    public static final String SSLKSLOCATION = "SSLKSLOCATION";
    public static final String SSLKSTYPE = "SSLKSTYPE";
    public static final String SSLTSLOCATION = "SSLTSLOCATION";
    public static final String SSLTSTYPE = "SSLTSTYPE";
    public static final String SSLTYPE = "SSLTYPE";
    public static final String HTTP_HEADER_NAME = "HTTP_HEADER_AUTH_HEADER_NAME";
    public static final String API_ID = "API_KEY_AUTH_API_ID_NAME";
    public static final String OAUTH2_CLIENT_ID = "OAUTH2_CLIENT_CREDENTIALS_CLIENT_ID_NAME";
    public static final String OAUTH2_SCOPE = "OAUTH2_CLIENT_CREDENTIALS_SCOPE_NAME";
    public static final String OAUTH2_URL_TOKEN_ENDPOINT = "OAUTH2_CLIENT_CREDENTIALS_URL_TOKEN_ENDPOINT_NAME";

    // --- credenziali ---
    public static final String SUBSCRIPTION_KEY = "SUBSCRIPTION_KEY_VALUE";
    public static final String HTTPPASSW = "HTTPPASSW";
    public static final String SSLKSPASSWD = "SSLKSPASSWD";
    public static final String SSLPKEYPASSWD = "SSLPKEYPASSWD";
    public static final String SSLTSPASSWD = "SSLTSPASSWD";
    public static final String HTTP_HEADER_VALUE = "HTTP_HEADER_AUTH_HEADER_VALUE";
    public static final String API_KEY = "API_KEY_AUTH_API_KEY_NAME";
    public static final String OAUTH2_CLIENT_SECRET = "OAUTH2_CLIENT_CREDENTIALS_CLIENT_SECRET_NAME";

    public static final Set<String> CONFIG_KEYS = Set.of(
            ABILITATO, URL, ABILITA_GDE, TIPOAUTENTICAZIONE, TIPOSSL, HTTPUSER,
            SSLKSLOCATION, SSLKSTYPE, SSLTSLOCATION, SSLTSTYPE, SSLTYPE,
            HTTP_HEADER_NAME, API_ID, OAUTH2_CLIENT_ID, OAUTH2_SCOPE, OAUTH2_URL_TOKEN_ENDPOINT);

    public static final Set<String> CREDENTIAL_KEYS = Set.of(
            SUBSCRIPTION_KEY, HTTPPASSW, SSLKSPASSWD, SSLPKEYPASSWD, SSLTSPASSWD,
            HTTP_HEADER_VALUE, API_KEY, OAUTH2_CLIENT_SECRET);
}
