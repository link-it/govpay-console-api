package it.govpay.console.impostazioni;

import it.govpay.common.configurazione.model.GoogleCaptcha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.common.configurazione.model.Hardening;
import it.govpay.console.model.ConfigurazioneReCaptcha;
import it.govpay.console.model.ImpostazioniHardeningCredenziali;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniHardening}
 * e {@link Hardening} (bean di {@code govpay-common}, deserializzato dal blob
 * {@code configurazione}). La chiave segreta non e' mai letta nel DTO di
 * configurazione: e' gestita da {@link #applyCredenziali}, chiamato
 * dall'endpoint dedicato.
 */
@Component
public class HardeningMapper {

    private static final Logger log = LoggerFactory.getLogger(HardeningMapper.class);

    /**
     * Default del prodotto per i campi numerici del captcha, gli stessi che il
     * core scrive in {@code Configurazione.getHardeningDefault()}. Servono
     * perche' in {@link GoogleCaptcha} i tre campi sono primitivi: lo zero non
     * e' distinguibile da "non impostato", e nessuno dei tre zeri e' un valore
     * sensato — zero come soglia accetta qualunque punteggio, zero come timeout
     * significa attesa illimitata sulla chiamata di verifica.
     */
    static final double SOGLIA_DEFAULT = 0.7d;
    static final int TIMEOUT_DEFAULT_MS = 5000;

    /** Intervallo che lo schema dichiara per {@code soglia}. */
    static final double SOGLIA_MIN = 0.1d;
    static final double SOGLIA_MAX = 1.0d;

    public it.govpay.console.model.ImpostazioniHardening toDto(Hardening source) {
        it.govpay.console.model.ImpostazioniHardening dto = new it.govpay.console.model.ImpostazioniHardening();
        dto.setAbilitato(source.isAbilitato());
        dto.setCaptcha(toCaptchaDto(source.getGoogleCatpcha()));
        return dto;
    }

    private static ConfigurazioneReCaptcha toCaptchaDto(GoogleCaptcha source) {
        ConfigurazioneReCaptcha captcha = new ConfigurazioneReCaptcha();
        if (source == null) {
            return captcha;
        }
        captcha.setServerURL(source.getServerURL());
        captcha.setSiteKey(source.getSiteKey());
        captcha.setSoglia(sogliaEsponibile(source.getSoglia()));
        captcha.setParametro(source.getResponseParameter());
        captcha.setDenyOnFail(source.isDenyOnFail());
        captcha.setConnectionTimeoutMs(source.getConnectionTimeout());
        captcha.setReadTimeoutMs(source.getReadTimeout());
        return captcha;
    }

    /**
     * Una soglia fuori dall'intervallo dichiarato non viene restituita come se
     * fosse valida: la si omette, che per un campo opzionale vuol dire "non
     * configurata". Sono valori che restano solo su configurazioni scritte
     * prima di questa correzione o dalla V1; restituirli tali e' quali
     * produceva una rappresentazione che la successiva scrittura rifiutava,
     * lasciando la risorsa non piu' salvabile dal cruscotto.
     */
    private static Double sogliaEsponibile(double soglia) {
        if (soglia < SOGLIA_MIN || soglia > SOGLIA_MAX) {
            log.warn("Soglia reCAPTCHA memorizzata fuori intervallo [{}, {}]: {}. "
                    + "Non viene esposta; la prossima scrittura la riporta al default {}.",
                    SOGLIA_MIN, SOGLIA_MAX, soglia, SOGLIA_DEFAULT);
            return null;
        }
        return soglia;
    }

    /** Applica il DTO su un {@link Hardening} esistente, preservando {@code secretKey}. */
    public void applyConfig(Hardening target, it.govpay.console.model.ImpostazioniHardening dto) {
        // `captcha` e' `required` nello schema e la Bean Validation e' applicata su
        // entrambi i percorsi di scrittura (`@Valid` sulla replace, RepresentationValidator
        // sul PATCH ricomposto): le guardie null qui erano irraggiungibili. Il controllo
        // su `target.getGoogleCatpcha()` sotto resta, perche' target e' l'oggetto comune
        // esistente e puo' non avere ancora il blocco captcha.
        target.setAbilitato(Boolean.TRUE.equals(dto.getAbilitato()));
        String secretKeyEsistente = target.getGoogleCatpcha() != null ? target.getGoogleCatpcha().getSecretKey() : null;

        ConfigurazioneReCaptcha captcha = dto.getCaptcha();
        GoogleCaptcha googleCaptcha = new GoogleCaptcha();
        googleCaptcha.setServerURL(captcha.getServerURL());
        googleCaptcha.setSiteKey(captcha.getSiteKey());
        // I campi assenti prendono il default del prodotto, non zero: zero non e'
        // un valore che l'API accetterebbe di rileggere (la soglia ha minimo 0.1)
        // ne' uno che il core interpreta come "non impostato".
        googleCaptcha.setSoglia(captcha.getSoglia() != null ? captcha.getSoglia() : SOGLIA_DEFAULT);
        googleCaptcha.setResponseParameter(captcha.getParametro());
        googleCaptcha.setDenyOnFail(Boolean.TRUE.equals(captcha.getDenyOnFail()));
        googleCaptcha.setConnectionTimeout(captcha.getConnectionTimeoutMs() != null
                ? captcha.getConnectionTimeoutMs() : TIMEOUT_DEFAULT_MS);
        googleCaptcha.setReadTimeout(captcha.getReadTimeoutMs() != null
                ? captcha.getReadTimeoutMs() : TIMEOUT_DEFAULT_MS);
        googleCaptcha.setSecretKey(secretKeyEsistente);
        target.setGoogleCatpcha(googleCaptcha);
    }

    /** Aggiorna solo la chiave segreta, se valorizzata (le altre credenziali non esistono qui). */
    public void applyCredenziali(Hardening target, ImpostazioniHardeningCredenziali credenziali) {
        if (credenziali.getSecretKey() == null) {
            return;
        }
        if (target.getGoogleCatpcha() == null) {
            target.setGoogleCatpcha(new GoogleCaptcha());
        }
        target.getGoogleCatpcha().setSecretKey(credenziali.getSecretKey());
    }
}
