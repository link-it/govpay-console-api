package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.Incasso;

public interface IncassoRepository extends JpaRepository<Incasso, Long>, JpaSpecificationExecutor<Incasso> {

    /** Chiave canonica del dettaglio: coincide col vincolo {@code unique_incassi_1} già presente a DB. */
    Optional<Incasso> findByCodDominioAndIdentificativo(String codDominio, String identificativo);

    /**
     * Sovrascrive {@code data_ora_incasso} col clock del DB anziché quello
     * applicativo: la query di pickup del batch (V1: {@code
     * WHERE data_ora_incasso < now()}) diventerebbe invisibile fino al
     * riallineamento se i due orologi divergono. Va chiamata subito dopo il
     * {@code save()} (che valorizza la colonna con un placeholder solo per
     * soddisfare il NOT NULL) e seguita da un {@code entityManager.refresh}
     * per sincronizzare l'istanza in memoria col valore reale scritto a DB.
     */
    @Modifying
    @Query(value = "update incassi set data_ora_incasso = CURRENT_TIMESTAMP where id = :id", nativeQuery = true)
    void touchDataOraIncasso(@Param("id") Long id);
}
