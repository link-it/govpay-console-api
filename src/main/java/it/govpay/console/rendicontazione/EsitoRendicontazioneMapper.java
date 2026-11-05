package it.govpay.console.rendicontazione;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.govpay.console.model.EsitoRendicontazione;

/**
 * Mappatura del codice {@code rendicontazioni.esito} sui 5 valori ammessi.
 * Un codice {@code null} o non riconosciuto restituisce {@code null}
 * (loggato a {@code warn} nel secondo caso).
 */
public final class EsitoRendicontazioneMapper {

    private static final Logger log = LoggerFactory.getLogger(EsitoRendicontazioneMapper.class);

    private EsitoRendicontazioneMapper() {
    }

    public static EsitoRendicontazione fromCodice(Integer codice) {
        if (codice == null) {
            return null;
        }
        return switch (codice) {
            case 0 -> EsitoRendicontazione.ESEGUITO;
            case 3 -> EsitoRendicontazione.REVOCATO;
            case 4 -> EsitoRendicontazione.ESEGUITO_STANDIN;
            case 8 -> EsitoRendicontazione.ESEGUITO_STANDIN_SENZA_RPT;
            case 9 -> EsitoRendicontazione.ESEGUITO_SENZA_RPT;
            default -> {
                log.warn("Codice esito rendicontazione non riconosciuto: {}", codice);
                yield null;
            }
        };
    }
}
