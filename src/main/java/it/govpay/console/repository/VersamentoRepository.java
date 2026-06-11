package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Versamento;

public interface VersamentoRepository
        extends JpaRepository<Versamento, Long>, JpaSpecificationExecutor<Versamento> {
}
