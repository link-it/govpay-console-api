package it.govpay.console.riconciliazione;

import java.time.OffsetDateTime;

import it.govpay.console.model.StatoRiconciliazione;

/**
 * Parametri normalizzati della ricerca {@code GET /riconciliazioni}.
 * {@code cursor} non null ⇔ modalità cursor attiva (stringa vuota = prima pagina).
 */
public record RiconciliazioneListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String idDominio,
        OffsetDateTime dataDa,
        OffsetDateTime dataA,
        StatoRiconciliazione stato,
        String sct,
        String idFlusso,
        String iuv) {
}
