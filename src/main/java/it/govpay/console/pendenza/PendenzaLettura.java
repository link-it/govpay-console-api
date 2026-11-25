package it.govpay.console.pendenza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.entity.PendenzaUoStorica;
import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.PendenzaVisibilita;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.repository.PendenzaRepository;
import it.govpay.pendenze.service.PosizioneDebitoriaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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

    @PersistenceContext
    private EntityManager entityManager;

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

        if (!PendenzaVisibilita.isVisibile(p.getIdDominio(), idUnitaOperativa, idTipoVersamento,
                () -> unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili()), operatore)) {
            log.debug("ACL nega l'accesso (404 anti-leak) idA2A={} idPendenza={} principal={}",
                    idA2A, idPendenza, operatore.principal());
            throw nonTrovata(idA2A, idPendenza);
        }
        return new Letta(p, contesto, idUnitaOperativa);
    }

    /**
     * Visibilita' di una pendenza gia' letta, per chi la raggiunge da un'altra risorsa
     * (ricevute): stessa regola della lettura. L'unita' operativa e' quella della posizione;
     * per una pendenza senza opzione di pagamento, che non ha posizione, e' quella storica
     * ({@link PendenzaUoStorica}). Senza unita' operativa la pendenza vale come quella
     * {@code EC} del suo dominio.
     *
     * @param pendenza pendenza con l'opzione e la posizione caricate, se presenti
     */
    public boolean isVisibile(Pendenza pendenza, OperatoreCorrente operatore) {
        OpzionePagamento opzione = pendenza.getOpzionePagamento();
        Long idUnitaOperativa;
        if (opzione != null) {
            idUnitaOperativa = opzione.getPosizioneDebitoria().getIdUnitaOperativa();
        } else {
            PendenzaUoStorica storica = entityManager.find(PendenzaUoStorica.class, pendenza.getId());
            idUnitaOperativa = storica != null ? storica.getIdUo() : null;
        }
        return PendenzaVisibilita.isVisibile(pendenza.getIdDominio(), idUnitaOperativa,
                pendenza.getIdTipoVersamento(),
                () -> unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili()), operatore);
    }

    private static NotFoundException nonTrovata(String idA2A, String idPendenza) {
        return new NotFoundException("Pendenza non trovata: idA2A=" + idA2A + ", idPendenza=" + idPendenza);
    }
}
