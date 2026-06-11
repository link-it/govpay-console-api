package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.GpAudit;

public interface GpAuditRepository extends JpaRepository<GpAudit, Long> {
}
