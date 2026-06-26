package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.IbanAccredito;

public interface IbanAccreditoRepository extends JpaRepository<IbanAccredito, Long> {
}
