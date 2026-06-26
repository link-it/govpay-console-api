package it.govpay.console.pendenza;

public record PendenzaListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String idPendenza,
        String numeroAvviso,
        String idDominio,
        String identificativoDebitore) {
}
