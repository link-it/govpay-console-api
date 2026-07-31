package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.console.model.ConfigurazioneReCaptcha;
import it.govpay.console.model.ImpostazioniHardeningCredenziali;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniHardening}
 * e l'entity JPA (riga singola). La chiave segreta non e' mai letta nel DTO
 * di configurazione: e' gestita da {@link #applyCredenziali}, chiamato
 * dall'endpoint dedicato.
 */
@Component
public class HardeningMapper {

    public it.govpay.console.model.ImpostazioniHardening toDto(it.govpay.console.entity.ImpostazioniHardening entity) {
        it.govpay.console.model.ImpostazioniHardening dto = new it.govpay.console.model.ImpostazioniHardening();
        dto.setAbilitato(entity.isAbilitato());
        dto.setCaptcha(toCaptchaDto(entity));
        return dto;
    }

    private static ConfigurazioneReCaptcha toCaptchaDto(it.govpay.console.entity.ImpostazioniHardening entity) {
        ConfigurazioneReCaptcha captcha = new ConfigurazioneReCaptcha();
        captcha.setServerURL(entity.getServerUrl());
        captcha.setSiteKey(entity.getSiteKey());
        captcha.setSoglia(entity.getSoglia());
        captcha.setParametro(entity.getParametro());
        captcha.setDenyOnFail(entity.isDenyOnFail());
        captcha.setConnectionTimeoutMs(entity.getConnectionTimeoutMs());
        captcha.setReadTimeoutMs(entity.getReadTimeoutMs());
        return captcha;
    }

    public void applyConfig(it.govpay.console.entity.ImpostazioniHardening entity,
                            it.govpay.console.model.ImpostazioniHardening dto) {
        entity.setAbilitato(Boolean.TRUE.equals(dto.getAbilitato()));
        ConfigurazioneReCaptcha captcha = dto.getCaptcha();
        entity.setServerUrl(captcha != null ? captcha.getServerURL() : null);
        entity.setSiteKey(captcha != null ? captcha.getSiteKey() : null);
        entity.setSoglia(captcha != null ? captcha.getSoglia() : null);
        entity.setParametro(captcha != null ? captcha.getParametro() : null);
        entity.setDenyOnFail(captcha != null && Boolean.TRUE.equals(captcha.getDenyOnFail()));
        entity.setConnectionTimeoutMs(captcha != null ? captcha.getConnectionTimeoutMs() : null);
        entity.setReadTimeoutMs(captcha != null ? captcha.getReadTimeoutMs() : null);
    }

    /** Aggiorna solo la chiave segreta, se valorizzata (le altre credenziali non esistono qui). */
    public void applyCredenziali(it.govpay.console.entity.ImpostazioniHardening entity,
                                 ImpostazioniHardeningCredenziali credenziali) {
        if (credenziali.getSecretKey() != null) {
            entity.setSecretKey(credenziali.getSecretKey());
        }
    }
}
