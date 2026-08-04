package it.govpay.console.impostazioni;

import it.govpay.common.configurazione.model.GoogleCaptcha;
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
        captcha.setSoglia(source.getSoglia());
        captcha.setParametro(source.getResponseParameter());
        captcha.setDenyOnFail(source.isDenyOnFail());
        captcha.setConnectionTimeoutMs(source.getConnectionTimeout());
        captcha.setReadTimeoutMs(source.getReadTimeout());
        return captcha;
    }

    /** Applica il DTO su un {@link Hardening} esistente, preservando {@code secretKey}. */
    public void applyConfig(Hardening target, it.govpay.console.model.ImpostazioniHardening dto) {
        target.setAbilitato(Boolean.TRUE.equals(dto.getAbilitato()));
        String secretKeyEsistente = target.getGoogleCatpcha() != null ? target.getGoogleCatpcha().getSecretKey() : null;

        ConfigurazioneReCaptcha captcha = dto.getCaptcha();
        GoogleCaptcha googleCaptcha = new GoogleCaptcha();
        googleCaptcha.setServerURL(captcha != null ? captcha.getServerURL() : null);
        googleCaptcha.setSiteKey(captcha != null ? captcha.getSiteKey() : null);
        googleCaptcha.setSoglia(captcha != null && captcha.getSoglia() != null ? captcha.getSoglia() : 0d);
        googleCaptcha.setResponseParameter(captcha != null ? captcha.getParametro() : null);
        googleCaptcha.setDenyOnFail(captcha != null && Boolean.TRUE.equals(captcha.getDenyOnFail()));
        googleCaptcha.setConnectionTimeout(captcha != null && captcha.getConnectionTimeoutMs() != null
                ? captcha.getConnectionTimeoutMs() : 0);
        googleCaptcha.setReadTimeout(captcha != null && captcha.getReadTimeoutMs() != null
                ? captcha.getReadTimeoutMs() : 0);
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
