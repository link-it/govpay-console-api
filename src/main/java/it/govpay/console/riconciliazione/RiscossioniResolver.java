package it.govpay.console.riconciliazione;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.govpay.console.repository.ConsoleRptRepository;
import it.govpay.pendenze.entity.Pagamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Lettura in blocco di {@code Rpt} e pendenza per una lista di {@link Pagamento}, necessarie a
 * {@link RiconciliazioneMapper} per risolvere {@code _links.ricevuta}/{@code _links.pendenza}:
 * condiviso tra {@link RiconciliazioneDetailService} e la registrazione (`PUT`, caso
 * idempotente con riscossioni già presenti) per evitare la duplicazione. Una query per
 * risorsa, indipendente dal numero di riscossioni.
 */
@Component
public class RiscossioniResolver {

    /** Riferimento pubblico di una pendenza: {@code (idA2A, idPendenza)}. */
    public record RiferimentoPendenza(String idA2A, String idPendenza) {
    }

    private final ConsoleRptRepository rptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RiscossioniResolver(ConsoleRptRepository rptRepository) {
        this.rptRepository = rptRepository;
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

    /**
     * {@code idVocePendenza -> (idA2A, idPendenza)} della pendenza di ciascuna voce
     * ({@code Pagamento.idSingoloVersamento}): una sola query, che legge solo le tre colonne.
     */
    public Map<Long, RiferimentoPendenza> loadPendenze(List<Pagamento> riscossioni) {
        Set<Long> ids = riscossioni.stream().map(Pagamento::getIdSingoloVersamento).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                        "select v.id, a.codApplicazione, p.idPendenza from VocePendenza v "
                                + "join v.pendenza p join Applicazione a on a.id = p.idApplicazione "
                                + "where v.id in :ids", Object[].class)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(r -> (Long) r[0],
                        r -> new RiferimentoPendenza((String) r[1], (String) r[2])));
    }
}
