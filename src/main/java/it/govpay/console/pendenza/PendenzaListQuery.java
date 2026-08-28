package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.util.List;

import it.govpay.console.model.StatoPendenza;

public record PendenzaListQuery(
        int page,
        int limit,
        String sort,
        Boolean total,
        String cursor,
        String idPendenza,
        String numeroAvviso,
        String idDominio,
        String identificativoDebitore,
        StatoPendenza stato,
        LocalDate dataDa,
        LocalDate dataA,
        String iuv,
        String direzione,
        String divisione,
        String idA2A,
        List<String> idTipoPendenza) {
}
