package it.govpay.console.stazione;

public record StazioneListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String codStazione,
        Boolean abilitato) {
}
