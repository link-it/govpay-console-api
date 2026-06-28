package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.TipoVersamento;

public interface TipoVersamentoRepository
        extends JpaRepository<TipoVersamento, Long>, JpaSpecificationExecutor<TipoVersamento> {

    Optional<TipoVersamento> findByCodTipoVersamento(String codTipoVersamento);

    boolean existsByCodTipoVersamento(String codTipoVersamento);
}
