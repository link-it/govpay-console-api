package it.govpay.console.pendenza;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.govpay.console.repository.UnitaOperativaRepository;
import it.govpay.console.security.OperatoreCorrente;
import it.govpay.console.security.PendenzaVisibilita;
import it.govpay.console.web.NotFoundException;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.service.PosizioneDebitoriaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Lettura di una posizione debitoria con i suoi debitori, le opzioni e le pendenze, e calcolo
 * della visibilita' di ciascuna pendenza per l'operatore. Condivisa dagli endpoint che
 * partono da {@code /posizioni-debitorie/{idA2A}/{idPosizioneDebitoria}}.
 */
@Component
public class PosizioneGrafo {

    private static final Logger log = LoggerFactory.getLogger(PosizioneGrafo.class);

    /**
     * Grafo letto: tutti i debitori (anche rimossi) con l'anagrafica, le opzioni con le loro
     * pendenze, e le anagrafiche delle pendenze.
     */
    public record Caricato(PosizioneDebitoria posizione, List<PdDebitore> pdDebitori,
                           List<OpzionePagamento> opzioni, PendenzaAnagrafiche.Pagina pagina) {

        public List<Pendenza> pendenze() {
            return opzioni.stream().flatMap(o -> o.getPendenze().stream()).toList();
        }
    }

    private final PosizioneDebitoriaService posizioneDebitoriaService;
    private final UnitaOperativaRepository unitaOperativaRepository;
    private final PendenzaAnagrafiche anagrafiche;

    @PersistenceContext
    private EntityManager entityManager;

    public PosizioneGrafo(PosizioneDebitoriaService posizioneDebitoriaService,
                          UnitaOperativaRepository unitaOperativaRepository,
                          PendenzaAnagrafiche anagrafiche) {
        this.posizioneDebitoriaService = posizioneDebitoriaService;
        this.unitaOperativaRepository = unitaOperativaRepository;
        this.anagrafiche = anagrafiche;
    }

    /** @throws NotFoundException se la posizione non esiste */
    public Caricato carica(String idA2A, String idPosizioneDebitoria) {
        PosizioneDebitoria posizione = posizioneDebitoriaService
                .trovaPerIdentificativo(idA2A, idPosizioneDebitoria)
                .orElseThrow(() -> nonTrovata(idA2A, idPosizioneDebitoria));

        // Il grafo si legge in due query, non per navigazione: i debitori (anche rimossi) con
        // la loro anagrafica, poi le opzioni con le pendenze. Le raccolte sono liste, e piu' di
        // una in un solo fetch non e' ammessa.
        List<PdDebitore> pdDebitori = entityManager.createQuery(
                        "select d from PdDebitore d join fetch d.debitore "
                                + "where d.posizioneDebitoria.id = :id order by d.posizione", PdDebitore.class)
                .setParameter("id", posizione.getId()).getResultList();
        List<OpzionePagamento> opzioni = entityManager.createQuery(
                        "select distinct o from OpzionePagamento o left join fetch o.pendenze "
                                + "where o.pdDebitore.posizioneDebitoria.id = :id order by o.id", OpzionePagamento.class)
                .setParameter("id", posizione.getId()).getResultList();

        List<Pendenza> tutte = opzioni.stream().flatMap(o -> o.getPendenze().stream()).toList();
        return new Caricato(posizione, pdDebitori, opzioni, anagrafiche.risolviPagina(tutte));
    }

    /**
     * Visibilita' di ogni pendenza per l'operatore, per id della pendenza: dominio e unita'
     * operativa della posizione, tipo della pendenza.
     */
    public Map<Long, Boolean> visibilita(Caricato caricato, OperatoreCorrente operatore) {
        Supplier<Set<Long>> dominiConUoEc = memoizzato(
                () -> unitaOperativaRepository.findDominioIdsConUoEcTra(operatore.idUoVisibili()));
        PosizioneDebitoria posizione = caricato.posizione();
        Map<Long, Boolean> visibili = new HashMap<>();
        for (Pendenza p : caricato.pendenze()) {
            PendenzaAnagrafiche.Contesto contesto = caricato.pagina().contesto(p);
            Long idTipoVersamento = contesto.tipoVersamento() != null ? contesto.tipoVersamento().getId() : null;
            visibili.put(p.getId(), PendenzaVisibilita.isVisibile(posizione.getIdDominio(),
                    posizione.getIdUnitaOperativa(), idTipoVersamento, dominiConUoEc, operatore));
        }
        return visibili;
    }

    public NotFoundException nonTrovata(String idA2A, String idPosizioneDebitoria) {
        log.debug("Posizione non trovata o non visibile idA2A={} idPosizioneDebitoria={}", idA2A, idPosizioneDebitoria);
        return new NotFoundException("Posizione debitoria non trovata: idA2A=" + idA2A
                + ", idPosizioneDebitoria=" + idPosizioneDebitoria);
    }

    private static Supplier<Set<Long>> memoizzato(Supplier<Set<Long>> origine) {
        Set<Long>[] valore = new Set[1];
        return () -> {
            if (valore[0] == null) {
                valore[0] = origine.get();
            }
            return valore[0];
        };
    }
}
