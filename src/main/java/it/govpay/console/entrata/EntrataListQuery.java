package it.govpay.console.entrata;

public record EntrataListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idEntrata,
        String descrizione) {
}
