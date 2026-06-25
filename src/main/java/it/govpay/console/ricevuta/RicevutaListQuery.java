package it.govpay.console.ricevuta;

import java.time.LocalDate;

/**
 * Parametri normalizzati della ricerca {@code GET /ricevute}. {@code cursor} non
 * null ⇔ modalità cursor attiva (anche stringa vuota = prima pagina cursor mode).
 */
public record RicevutaListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String iuv,
        String idDominio,
        String idRicevuta,
        LocalDate dataDa,
        LocalDate dataA) {
}
