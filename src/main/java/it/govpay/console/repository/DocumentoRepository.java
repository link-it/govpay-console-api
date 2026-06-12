package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Documento;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
}
