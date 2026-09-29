package it.govpay.console.intermediario;

public record IntermediarioListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idIntermediario,
        String denominazione,
        Boolean abilitato) {
}
