package it.govpay.console.entecreditore;

public record EnteCreditoreListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String search) {
}
