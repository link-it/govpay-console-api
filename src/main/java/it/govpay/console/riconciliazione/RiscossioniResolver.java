package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.pendenze.entity.Pagamento;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.console.repository.SingoloVersamentoRepository;

/**
 * Batch-fetch di {@code Rpt}/{@link SingoloVersamento} per una lista di
 * {@link Pagamento}, necessari a {@link RiconciliazioneMapper} per risolvere
 * {@code _links.ricevuta}/{@code _links.pendenza} — condiviso tra
 * {@link RiconciliazioneDetailService} e la registrazione (`PUT`, caso
 * idempotente con riscossioni già presenti) per evitare la duplicazione.
 */
@Component
public class RiscossioniResolver {

    private final ConsoleRptRepository rptRepository;
    private final SingoloVersamentoRepository singoloVersamentoRepository;

    public RiscossioniResolver(ConsoleRptRepository rptRepository, SingoloVersamentoRepository singoloVersamentoRepository) {
        this.rptRepository = rptRepository;
        this.singoloVersamentoRepository = singoloVersamentoRepository;
    }

    /** {@code idRpt -> iur} delle RT collegate alle riscossioni, senza leggere i corpi XML. */
    public Map<Long, String> loadIurRpt(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getRpt).filter(Objects::nonNull).map(rpt -> rpt.getId())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return rptRepository.findIurByIdIn(ids).stream()
                .collect(Collectors.toMap(ConsoleRptRepository.RptIur::getId, ConsoleRptRepository.RptIur::getIur));
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
