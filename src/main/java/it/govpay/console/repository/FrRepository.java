package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import it.govpay.console.entity.Fr;

public interface FrRepository extends JpaRepository<Fr, Long>, JpaSpecificationExecutor<Fr> {
}
