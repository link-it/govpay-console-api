package it.govpay.console.tipopendenzadominio;

import it.govpay.console.model.TipoPendenzaAvvisaturaMail;
import it.govpay.console.model.TipoPendenzaDominioAvvisaturaAppIo;
import it.govpay.console.model.TipoPendenzaPortaleBackoffice;
import it.govpay.console.model.TipoPendenzaPortalePagamento;
import it.govpay.console.model.TipoPendenzaTracciatoCsv;

/**
 * I campi scrivibili di una tipologia di pendenza di dominio. Differisce da
 * {@code DatiTipoPendenza} per l'assenza di {@code descrizione}, che vive solo sul
 * tipo pendenza globale, e per il blocco App IO, che qui porta anche l'{@code apiKey}.
 */
public record DatiTipoPendenzaDominio(String codificaIUV, Boolean pagaTerzi, Boolean abilitato,
        TipoPendenzaPortaleBackoffice portaleBackoffice, TipoPendenzaPortalePagamento portalePagamento,
        TipoPendenzaAvvisaturaMail avvisaturaMail, TipoPendenzaDominioAvvisaturaAppIo avvisaturaAppIo,
        Object visualizzazione, TipoPendenzaTracciatoCsv tracciatoCsv) {
}
