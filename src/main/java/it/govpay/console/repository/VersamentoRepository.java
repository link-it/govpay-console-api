package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import it.govpay.console.entity.Versamento;

public interface VersamentoRepository
        extends JpaRepository<Versamento, Long>, JpaSpecificationExecutor<Versamento> {

    /**
     * Override del findAll di JpaSpecificationExecutor con EntityGraph: carica
     * in un'unica query le ref usate dalla proiezione summary (no N+1).
     */
    @Override
    @EntityGraph(attributePaths = {"dominio", "applicazione", "tipoVersamento", "unitaOperativa",
            "tipoVersamentoDominio.tipoVersamento"})
    Page<Versamento> findAll(@Nullable Specification<Versamento> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"dominio", "applicazione", "tipoVersamento", "unitaOperativa",
            "tipoVersamentoDominio.tipoVersamento", "singoliVersamenti",
            "singoliVersamenti.ibanAccredito", "singoliVersamenti.ibanAppoggio",
            "singoliVersamenti.dominio", "singoliVersamenti.tributo.tipoTributo", "documento"})
    @Query("select v from Versamento v where v.applicazione.codApplicazione = :idA2A and v.codVersamentoEnte = :idPendenza")
    Optional<Versamento> findDetail(@Param("idA2A") String idA2A, @Param("idPendenza") String idPendenza);
}
