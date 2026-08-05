package it.govpay.console.eventi;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Parametri gia' risolti (default applicati, ACL espansa) per una chiamata a
 * {@code GET /eventi} sul servizio GDE. I filtri enum sono stringhe (il nome
 * costante, identico tra l'enum di console-api e quello del client GDE):
 * {@link EventoGdeClient} resta cosi' indipendente dai tipi enum generati.
 */
public record EventoGdeQuery(
        int limit,
        long offset,
        boolean cursorMode,
        OffsetDateTime cursorData,
        Long cursorId,
        boolean total,
        OffsetDateTime dataDa,
        OffsetDateTime dataA,
        List<String> idDominio,
        String iuv,
        String ccp,
        String idA2A,
        String idPendenza,
        String componente,
        String categoria,
        String esito,
        String ruolo,
        String tipoEvento,
        String sottotipoEvento,
        Integer severitaDa,
        Integer severitaA,
        String messaggi) {
}
