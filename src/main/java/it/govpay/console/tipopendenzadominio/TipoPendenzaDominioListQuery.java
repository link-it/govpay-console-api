package it.govpay.console.tipopendenzadominio;

public record TipoPendenzaDominioListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idTipoPendenza,
        String descrizione,
        Boolean abilitato) {
}
