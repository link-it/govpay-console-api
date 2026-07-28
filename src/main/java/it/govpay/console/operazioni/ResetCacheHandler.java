package it.govpay.console.operazioni;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder: console-api non ha oggi nessuna cache applicativa (nessun
 * {@code @EnableCaching}, nessun client govpay-common in modalita' {@code WithCache}).
 * L'operazione resta nel catalogo di esempio a dimostrazione del meccanismo
 * {@link OperazioneLocaleHandler}; {@link #eseguire()} e' un no-op
 * esplicito finche' non esiste una cache reale da svuotare.
 */
@Component
public class ResetCacheHandler implements OperazioneLocaleHandler {

    private static final Logger log = LoggerFactory.getLogger(ResetCacheHandler.class);

    @Override
    public String getId() {
        return "RESET_CACHE";
    }

    @Override
    public String getNome() {
        return "Reset cache applicativa";
    }

    @Override
    public String getDescrizione() {
        return "Operazione locale, non backed da un job batch. Placeholder: nessuna cache applicativa da svuotare oggi.";
    }

    @Override
    public void eseguire() {
        log.info("RESET_CACHE invocata: no-op, nessuna cache applicativa presente in console-api.");
    }
}
