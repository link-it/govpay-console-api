package it.govpay.console.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Applicazione;

public interface ApplicazioneRepository extends JpaRepository<Applicazione, Long> {
}
