package it.govpay.console.applicazione;

public record ApplicazioneListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String idA2A,
        String principal,
        Boolean abilitato) {
}
