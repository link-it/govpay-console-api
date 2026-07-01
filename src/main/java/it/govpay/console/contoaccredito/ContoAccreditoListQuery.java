package it.govpay.console.contoaccredito;

public record ContoAccreditoListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String descrizione,
        Boolean abilitato) {
}
