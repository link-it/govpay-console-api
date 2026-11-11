package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Pagamento;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /** Le riscossioni di una riconciliazione, per il dettaglio {@code riscossioni[]}. */
    List<Pagamento> findByIdIncassoOrderByDataPagamentoAsc(Long idIncasso);

    /** I pagamenti di una RT, per indice, per {@code Ricevuta.voci[]}. */
    List<Pagamento> findByIdRptOrderByIndiceDatiAsc(Long idRpt);
}
