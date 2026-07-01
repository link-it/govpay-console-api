package it.govpay.console.connettoredominio;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import it.govpay.console.connettore.ConnettoreProprietaKeys;

/**
 * Chiavi {@code cod_proprieta} della tabella EAV {@code connettori} usate dai
 * connettori di notifica pagamenti del dominio, allineate a quelle storiche di
 * GovPay. Le chiavi specifiche della notifica sono definite qui; quelle di
 * autenticazione/credenziale sono condivise con i connettori intermediario e
 * riusate da {@link ConnettoreProprietaKeys}.
 */
public final class ConnettoreDominioProprietaKeys {

    private ConnettoreDominioProprietaKeys() {
    }

    // --- config specifiche della notifica pagamenti ---
    public static final String ABILITATO = "ABILITATO";
    public static final String TIPO_CONNETTORE = "TIPO_CONNETTORE";
    public static final String TIPO_TRACCIATO = "TIPO_TRACCIATO";
    public static final String VERSIONE_CSV = "VERSIONE_CSV";
    public static final String CODICE_IPA = "CODICE_IPA";
    public static final String CODICE_CLIENTE = "CODICE_CLIENTE";
    public static final String CODICE_ISTITUTO = "CODICE_ISTITUTO";
    public static final String EMAIL_INDIRIZZO = "EMAIL_INDIRIZZO";
    public static final String EMAIL_SUBJECT = "EMAIL_SUBJECT";
    public static final String EMAIL_ALLEGATO = "EMAIL_ALLEGATO";
    public static final String DOWNLOAD_BASE_URL = "DOWNLOAD_BASE_URL";
    public static final String FILE_SYSTEM_PATH = "FILE_SYSTEM_PATH";
    public static final String TIPI_PENDENZA = "TIPI_PENDENZA";
    public static final String CONTENUTI = "CONTENUTI";
    public static final String INTERVALLO_CREAZIONE_TRACCIATO = "INTERV_CREAZ_TRAC";
    public static final String INVIA_TRACCIATO_ESITO = "INVIA_TRACCIATO_ESITO";
    /** Versione delle API REST (canale GovPay), es. {@code REST_1}. */
    public static final String VERSIONE = "VERSIONE";

    // --- auth (riuso delle chiavi condivise) ---
    public static final String URL = ConnettoreProprietaKeys.URL;
    public static final String TIPOAUTENTICAZIONE = ConnettoreProprietaKeys.TIPOAUTENTICAZIONE;
    public static final String TIPOSSL = ConnettoreProprietaKeys.TIPOSSL;
    public static final String HTTPUSER = ConnettoreProprietaKeys.HTTPUSER;
    public static final String SSLKSLOCATION = ConnettoreProprietaKeys.SSLKSLOCATION;
    public static final String SSLKSTYPE = ConnettoreProprietaKeys.SSLKSTYPE;
    public static final String SSLTSLOCATION = ConnettoreProprietaKeys.SSLTSLOCATION;
    public static final String SSLTSTYPE = ConnettoreProprietaKeys.SSLTSTYPE;
    public static final String SSLTYPE = ConnettoreProprietaKeys.SSLTYPE;
    public static final String HTTP_HEADER_NAME = ConnettoreProprietaKeys.HTTP_HEADER_NAME;
    public static final String API_ID = ConnettoreProprietaKeys.API_ID;
    public static final String OAUTH2_CLIENT_ID = ConnettoreProprietaKeys.OAUTH2_CLIENT_ID;
    public static final String OAUTH2_SCOPE = ConnettoreProprietaKeys.OAUTH2_SCOPE;
    public static final String OAUTH2_URL_TOKEN_ENDPOINT = ConnettoreProprietaKeys.OAUTH2_URL_TOKEN_ENDPOINT;

    private static final Set<String> NOTIFICA_CONFIG_KEYS = Set.of(
            ABILITATO, TIPO_CONNETTORE, TIPO_TRACCIATO, VERSIONE_CSV, CODICE_IPA, CODICE_CLIENTE,
            CODICE_ISTITUTO, EMAIL_INDIRIZZO, EMAIL_SUBJECT, EMAIL_ALLEGATO, DOWNLOAD_BASE_URL,
            FILE_SYSTEM_PATH, TIPI_PENDENZA, CONTENUTI, INTERVALLO_CREAZIONE_TRACCIATO,
            INVIA_TRACCIATO_ESITO, VERSIONE, URL, TIPOAUTENTICAZIONE, TIPOSSL, HTTPUSER,
            SSLKSLOCATION, SSLKSTYPE, SSLTSLOCATION, SSLTSTYPE, SSLTYPE, HTTP_HEADER_NAME,
            API_ID, OAUTH2_CLIENT_ID, OAUTH2_SCOPE, OAUTH2_URL_TOKEN_ENDPOINT);

    public static final Set<String> CONFIG_KEYS = NOTIFICA_CONFIG_KEYS;

    /** Le credenziali coincidono con quelle dei connettori intermediario. */
    public static final Set<String> CREDENTIAL_KEYS = ConnettoreProprietaKeys.CREDENTIAL_KEYS;

    static {
        // Difesa contro sovrapposizioni accidentali tra config e credenziali.
        Set<String> overlap = Stream.concat(CONFIG_KEYS.stream(), CREDENTIAL_KEYS.stream())
                .filter(k -> CONFIG_KEYS.contains(k) && CREDENTIAL_KEYS.contains(k))
                .collect(Collectors.toSet());
        if (!overlap.isEmpty()) {
            throw new IllegalStateException("Chiavi config/credenziali sovrapposte: " + overlap);
        }
    }
}
