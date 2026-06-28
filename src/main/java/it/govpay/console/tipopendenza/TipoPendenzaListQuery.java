package it.govpay.console.tipopendenza;

public record TipoPendenzaListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idTipoPendenza,
        String descrizione,
        Boolean abilitato) {
}
