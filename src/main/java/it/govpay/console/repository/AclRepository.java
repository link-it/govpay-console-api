package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.govpay.console.entity.Acl;

public interface AclRepository extends JpaRepository<Acl, Long> {

    List<Acl> findByIdUtenza(Long idUtenza);
}
