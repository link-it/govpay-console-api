package it.govpay.console.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.RtRecupero;

public interface RtRecuperoRepository extends JpaRepository<RtRecupero, Long> {

    /**
     * Tutte le righe sulla tripla, marcate o pendenti: può restituirne più di
     * una (nessuna deduplica sulle righe pendenti, issue #59 §10 / #17 §A).
     * Non va usata per decidere il riuso — vedi
     * {@link #findFirstByCodDominioAndIuvAndIurAndEsitoIsNotNull}.
     */
    List<RtRecupero> findByCodDominioAndIuvAndIur(String codDominio, String iuv, String iur);

    /**
     * Solo le righe **marcate** (issue #59 §10 / #17 §A): quelle vanno
     * riattivate quando l'operatore ripete la tripla, invece di accumularsi
     * a ogni tentativo. Le righe ancora pendenti ({@code esito IS NULL}) sono
     * volutamente escluse: la richiesta ripetuta su una tripla non ancora
     * elaborata resta indipendente e ne inserisce una nuova — nessuna
     * deduplica, {@code api-pagopa} risponde comunque {@code PAA_RECEIPT_DUPLICATA}
     * se una delle due arriva a buon fine per prima. Non esiste un vincolo
     * unique sulla tripla proprio per questo: più righe pendenti sono lecite.
     */
    Optional<RtRecupero> findFirstByCodDominioAndIuvAndIurAndEsitoIsNotNull(String codDominio, String iuv, String iur);

    /**
     * Riporta la riga allo stato "da elaborare" con {@code data_richiesta} dal
     * clock del DB, non da quello applicativo (stesso motivo di
     * {@link it.govpay.console.repository.IncassoRepository#touchDataOraIncasso}:
     * valori scritti con l'orologio sbagliato confonderebbero ordinamento e diagnostica).
     * Usata sia dopo l'insert di una riga nuova (che nasce con un placeholder
     * solo per soddisfare il {@code NOT NULL}) sia per il "sovrascrivi" di una
     * riga già marcata quando l'operatore ripete la stessa tripla (issue #59 §10):
     * in entrambi i casi il record deve ripartire da {@code esito IS NULL}.
     */
    @Modifying
    @Query(value = "update rt_recuperi set data_richiesta = CURRENT_TIMESTAMP, id_operatore = :idOperatore, "
            + "esito = NULL, data_ultimo_tentativo = NULL where id = :id", nativeQuery = true)
    void riavviaRichiesta(@Param("id") Long id, @Param("idOperatore") Long idOperatore);
}
