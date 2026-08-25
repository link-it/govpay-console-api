package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Pagamento;
import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.repository.RptRepository;
import it.govpay.console.repository.SingoloVersamentoRepository;

/**
 * Batch-fetch di {@link Rpt}/{@link SingoloVersamento} per una lista di
 * {@link Pagamento}, necessari a {@link RiconciliazioneMapper} per risolvere
 * {@code _links.ricevuta}/{@code _links.pendenza} — condiviso tra
 * {@link RiconciliazioneDetailService} e la registrazione (`PUT`, caso
 * idempotente con riscossioni già presenti) per evitare la duplicazione.
 */
@Component
public class RiscossioniResolver {

    private final RptRepository rptRepository;
    private final SingoloVersamentoRepository singoloVersamentoRepository;

    public RiscossioniResolver(RptRepository rptRepository, SingoloVersamentoRepository singoloVersamentoRepository) {
        this.rptRepository = rptRepository;
        this.singoloVersamentoRepository = singoloVersamentoRepository;
    }

    public Map<Long, Rpt> loadRpt(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getIdRpt).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return rptRepository.findAllById(ids).stream().collect(Collectors.toMap(Rpt::getId, r -> r));
    }

    public Map<Long, SingoloVersamento> loadSingoliVersamenti(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getIdSingoloVersamento).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return singoloVersamentoRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(SingoloVersamento::getId, sv -> sv));
    }
}
