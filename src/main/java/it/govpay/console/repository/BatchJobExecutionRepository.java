package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.BatchJobExecutionEntity;

public interface BatchJobExecutionRepository extends JpaRepository<BatchJobExecutionEntity, Long> {
}
