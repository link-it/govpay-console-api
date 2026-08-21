package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Tracciato;

public interface TracciatoRepository extends JpaRepository<Tracciato, Long>, JpaSpecificationExecutor<Tracciato> {
}
