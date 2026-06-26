package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.SingoloVersamento;

public interface SingoloVersamentoRepository extends JpaRepository<SingoloVersamento, Long> {
}
