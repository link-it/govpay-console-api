package it.govpay.console.entratadominio;

public record EntrataDominioListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idEntrata,
        String descrizione,
        Boolean abilitato) {
}
