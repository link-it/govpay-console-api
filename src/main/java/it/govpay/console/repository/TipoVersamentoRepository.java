package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.TipoVersamento;

public interface TipoVersamentoRepository extends JpaRepository<TipoVersamento, Long> {
}
