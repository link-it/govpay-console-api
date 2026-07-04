package it.govpay.console.operatore;

public record OperatoreListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String principal,
        String nome,
        Boolean abilitato) {
}
