package it.govpay.console.ruolo;

public record RuoloListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idRuolo) {
}
