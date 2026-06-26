package it.govpay.console.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import it.govpay.common.auth.GovpayAuthProperties;
import it.govpay.console.model.AutenticazioneEnum;
import it.govpay.console.model.MetodiAutenticazione;
import it.govpay.console.model.MetodoAutenticazione;

/**
 * Risolve i metodi di autenticazione attualmente abilitati leggendo le
 * {@link GovpayAuthProperties}. Esposto via {@code GET /auth/methods}
 * dall'{@code AuthMethodsController} per il rendering dei pulsanti di
 * login lato frontend.
 *
 * <p>Etichette in italiano hard-coded; {@code urlInizio} valorizzato
 * solo per FORM (il path del filter JSON login) — gli altri metodi sono
 * in-band (auth sulla call stessa).
 *
 * <p>TODO: localizzazione delle etichette via {@code MessageSource} quando
 * il progetto introdurra' i18n.
 */
@Service
public class MetodiAutenticazioneResolver {

    private final GovpayAuthProperties properties;

    public MetodiAutenticazioneResolver(GovpayAuthProperties properties) {
        this.properties = properties;
    }

    public MetodiAutenticazione resolve() {
        List<MetodoAutenticazione> metodi = new ArrayList<>();
        if (properties.getBasic().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.BASIC,
                    "Username e password (HTTP Basic)",
                    null,
                    "Autenticazione con header Authorization: Basic ad ogni richiesta."));
        }
        if (properties.getForm().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.FORM,
                    "Username e password",
                    properties.getForm().getLoginPath(),
                    "Login con credenziali in body JSON; la sessione e' poi propagata via cookie."));
        }
        if (properties.getLdap().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.LDAP,
                    "LDAP",
                    null,
                    "Autenticazione contro la directory LDAP configurata."));
        }
        if (properties.getSsl().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.SSL,
                    "Certificato client (mTLS)",
                    null,
                    "Autenticazione con certificato X.509 presentato a livello TLS."));
        }
        if (properties.getSslHeader().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.SSL_HEADER,
                    "Certificato client (proxy-terminated)",
                    null,
                    "Autenticazione con certificato propagato dal reverse proxy via header."));
        }
        if (properties.getHeader().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.HEADER,
                    "Pre-auth header",
                    null,
                    "Autenticazione tramite identita' gia' verificata upstream e propagata via header."));
        }
        if (properties.getApiKey().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.API_KEY,
                    "API Key",
                    null,
                    "Coppia di header id/key per autenticare client server-to-server."));
        }
        if (properties.getSpid().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.SPID,
                    "SPID",
                    null,
                    "Autenticazione tramite identita' SPID propagata dal proxy IdP."));
        }
        if (properties.getSession().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.SESSION,
                    "Sessione esterna",
                    null,
                    "Riuso di una sessione applicativa esistente."));
        }
        if (properties.getOauth2().isEnabled()) {
            metodi.add(metodo(AutenticazioneEnum.OAUTH2,
                    "OAuth2 / OpenID Connect",
                    null,
                    "Autenticazione tramite token Bearer JWT emesso da un provider OAuth2."));
        }
        return new MetodiAutenticazione().metodi(metodi);
    }

    private static MetodoAutenticazione metodo(AutenticazioneEnum codice,
                                               String etichetta,
                                               String urlInizio,
                                               String descrizione) {
        MetodoAutenticazione m = new MetodoAutenticazione(codice);
        m.setEtichetta(etichetta);
        m.setUrlInizio(urlInizio);
        m.setDescrizione(descrizione);
        return m;
    }
}
