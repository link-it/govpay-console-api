package it.govpay.console.unitaoperativa;

public record UnitaOperativaListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String ragioneSociale,
        Boolean abilitato) {
}
