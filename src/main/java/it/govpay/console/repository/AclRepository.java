package it.govpay.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.govpay.console.entity.Acl;

public interface AclRepository extends JpaRepository<Acl, Long> {

    List<Acl> findByIdUtenza(Long idUtenza);

    void deleteByIdUtenza(Long idUtenza);

    /**
     * Catalogo dei ruoli definiti: un ruolo esiste se ha almeno una riga ACL
     * di definizione, ovvero con {@code id_utenza IS NULL} (allineato a V1
     * {@code RuoliDAO}/{@code existsAcl(ruolo, null, servizio)}).
     */
    @Query("select distinct a.ruolo from Acl a where a.idUtenza is null and a.ruolo is not null")
    List<String> findRuoliCatalogo();
}
