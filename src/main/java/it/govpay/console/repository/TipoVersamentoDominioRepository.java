package it.govpay.console.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.TipoVersamentoDominio;

public interface TipoVersamentoDominioRepository
        extends JpaRepository<TipoVersamentoDominio, Long>, JpaSpecificationExecutor<TipoVersamentoDominio> {

    Optional<TipoVersamentoDominio> findByDominio_IdAndTipoVersamento_CodTipoVersamento(
            Long idDominio, String codTipoVersamento);

    boolean existsByDominio_IdAndTipoVersamento_CodTipoVersamento(Long idDominio, String codTipoVersamento);
}
