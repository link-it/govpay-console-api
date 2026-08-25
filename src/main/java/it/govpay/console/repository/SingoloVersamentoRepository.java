package it.govpay.console.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.SingoloVersamento;

public interface SingoloVersamentoRepository extends JpaRepository<SingoloVersamento, Long> {

    /** Carica anche {@code versamento}/{@code versamento.applicazione}, servono per idA2A/idPendenza. */
    @EntityGraph(attributePaths = {"versamento", "versamento.applicazione"})
    List<SingoloVersamento> findByIdIn(Collection<Long> ids);
}
