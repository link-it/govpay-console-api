package it.govpay.console.intermediario;

public record IntermediarioListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String codIntermediario,
        String denominazione,
        Boolean abilitato) {
}
