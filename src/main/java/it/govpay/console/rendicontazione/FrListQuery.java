package it.govpay.console.rendicontazione;

import java.time.OffsetDateTime;

import it.govpay.console.model.StatoFlussoRendicontazione;

/**
 * Parametri normalizzati della ricerca {@code GET /flussi-rendicontazione}.
 * {@code cursor} non null ⇔ modalità cursor attiva (stringa vuota = prima pagina).
 */
public record FrListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String idDominio,
        String idFlusso,
        String idPsp,
        OffsetDateTime dataDa,
        OffsetDateTime dataA,
        StatoFlussoRendicontazione stato,
        Boolean incassato,
        String iuv) {
}
