package it.govpay.console.pendenza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.VersamentoVisibilita;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.repository.PendenzaRepository;
import it.govpay.pendenze.service.PosizioneDebitoriaService;

/**
 * Lettura di una pendenza per identificativo, con la verifica di visibilita' per l'operatore:
 * punto unico per tutti gli endpoint che partono da {@code /pendenze/{idA2A}/{idPendenza}}.
 *
 * <p>Una pendenza inesistente, senza opzione di pagamento o non visibile all'operatore
 * risponde allo stesso modo (404), cosi' da non rivelarne l'esistenza.
 */
@Component
public class PendenzaLettura {

    private static final Logger log = LoggerFactory.getLogger(PendenzaLettura.class);

    /** Pendenza letta con voci, opzione di pagamento e posizione debitoria, e il suo contesto. */
    public record Letta(Pendenza pendenza, PendenzaAnagrafiche.Contesto contesto, Long idUnitaOperativa) {
    }

    private final PosizioneDebitoriaService posizioneDebitoriaService;
    private final PendenzaRepository pendenzaRepository;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final PendenzaAnagrafiche anagrafiche;

    public PendenzaLettura(PosizioneDebitoriaService posizioneDebitoriaService,
                           PendenzaRepository pendenzaRepository,
                           UnitaOperativaRepository unitaOperativaRepository,
                           PendenzaAnagrafiche anagrafiche) {
        this.posizioneDebitoriaService = posizioneDebitoriaService;
        this.pendenzaRepository = pendenzaRepository;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.anagrafiche = anagrafiche;
    }

    /**
     * Legge la pendenza se esiste, ha un'opzione di pagamento ed e' visibile all'operatore.
     *
     * @throws NotFoundException negli altri casi
     */
    public Letta leggiVisibile(String idA2A, String idPendenza, OperatoreCorrente operatore) {
        // Esclude le pendenze senza opzione di pagamento, che il modello non ammette.
        Long id = posizioneDebitoriaService.trovaPendenzaPerIdentificativo(idA2A, idPendenza)
                .map(Pendenza::getId)
                .orElseThrow(() -> nonTrovata(idA2A, idPendenza));
        // Voci, opzione di pagamento e posizione debitoria in una sola query.
        Pendenza p = pendenzaRepository.findByIdConVociEOpzione(id)
                .orElseThrow(() -> nonTrovata(idA2A, idPendenza));
        Long idUnitaOperativa = p.getOpzionePagamento().getPosizioneDebitoria().getIdUnitaOperativa();

        PendenzaAnagrafiche.Contesto contesto =
                anagrafiche.risolviContesto(p.getIdDominio(), p.getIdTipoPendenza(), idUnitaOperativa);
        Long idTipoVersamento = contesto.tipoVersamento() != null ? contesto.tipoVersamento().getId() : null;

        if (!VersamentoVisibilita.isVisibile(p.getIdDominio(), idUnitaOperativa, idTipoVersamento,
                () -> unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili()), operatore)) {
            log.debug("ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={} principal={}",
                    idA2A, idPendenza, operatore.principal());
            throw nonTrovata(idA2A, idPendenza);
        }
        return new Letta(p, contesto, idUnitaOperativa);
    }

    private static NotFoundException nonTrovata(String idA2A, String idPendenza) {
        return new NotFoundException("Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
    }
}
