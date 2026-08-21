package it.govpay.console.tracciato;

import java.time.OffsetDateTime;

import it.govpay.console.model.FormatoTracciato;
import it.govpay.console.model.StatoTracciatoPendenza;

public record TracciatoListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String idDominio,
        StatoTracciatoPendenza stato,
        OffsetDateTime dataDa,
        OffsetDateTime dataA,
        String operatoreMittente,
        FormatoTracciato formatoRichiesta) {
}
