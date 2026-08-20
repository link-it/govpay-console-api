package it.govpay.console.eventi;

import java.time.OffsetDateTime;

import it.govpay.console.model.CategoriaEvento;
import it.govpay.console.model.ComponenteEvento;
import it.govpay.console.model.EsitoEvento;
import it.govpay.console.model.RuoloEvento;

/**
 * Parametri grezzi di {@code GET /eventi}, cosi' come arrivano dal controller.
 * {@code cursor == null} → modalita' offset; {@code cursor != null} (anche
 * stringa vuota, prima pagina) → modalita' cursor. Stesso pattern di
 * {@code FrListQuery}.
 */
public record EventoListQuery(
        int page,
        int limit,
        Boolean total,
        String cursor,
        OffsetDateTime dataDa,
        OffsetDateTime dataA,
        String idDominio,
        String iuv,
        String ccp,
        String idA2A,
        String idPendenza,
        ComponenteEvento componente,
        CategoriaEvento categoria,
        EsitoEvento esito,
        RuoloEvento ruolo,
        String tipoEvento,
        String sottotipoEvento,
        Integer severitaDa,
        Integer severitaA,
        String messaggi) {
}
