package it.govpay.console.operazioni;

/**
 * Lanciata quando un'operazione senza {@code url} configurato non ha
 * nemmeno un {@link OperazioneLocaleHandler} registrato per il suo id:
 * l'avvio manuale non e' cablato per errore di configurazione, non per
 * colpa del client. Mappata a 503 problem+json.
 */
public class OperazioneTriggerNonConfiguratoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperazioneTriggerNonConfiguratoException(String idOperazione) {
        super("L'operazione '" + idOperazione + "' non e' configurata per l'avvio manuale (trigger URL mancante).");
    }
}
