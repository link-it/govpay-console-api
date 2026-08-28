package it.govpay.console.tipopendenza;

import it.govpay.console.model.TipoPendenzaAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaAvvisaturaMail;
import it.govpay.console.model.TipoPendenzaPortaleBackoffice;
import it.govpay.console.model.TipoPendenzaPortalePagamento;
import it.govpay.console.model.TipoPendenzaTracciatoCsv;

/**
 * I campi scrivibili di una tipologia di pendenza globale. Raccolti in un record
 * perche' erano dieci parametri passati posizionalmente da tre percorsi
 * (create, replace, patch), con quattro blocchi di configurazione dello stesso
 * "peso" visivo adiacenti fra loro.
 */
public record DatiTipoPendenza(String descrizione, String codificaIUV, Boolean pagaTerzi,
        Boolean abilitato, TipoPendenzaPortaleBackoffice portaleBackoffice,
        TipoPendenzaPortalePagamento portalePagamento, TipoPendenzaAvvisaturaMail avvisaturaMail,
        TipoPendenzaAvvisaturaAppIo avvisaturaAppIo, Object visualizzazione,
        TipoPendenzaTracciatoCsv tracciatoCsv) {
}
