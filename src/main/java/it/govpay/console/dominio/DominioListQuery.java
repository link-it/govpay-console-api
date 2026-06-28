package it.govpay.console.dominio;

public record DominioListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idDominio,
        String ragioneSociale,
        Boolean abilitato) {
}
