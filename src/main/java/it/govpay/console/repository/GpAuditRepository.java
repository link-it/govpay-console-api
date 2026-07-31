package it.govpay.console.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.govpay.console.entity.GpAudit;

public interface GpAuditRepository extends JpaRepository<GpAudit, Long> {

    @Query("select max(a.data) from GpAudit a where a.tipoOggetto in :azioni")
    Optional<OffsetDateTime> findUltimaModifica(@Param("azioni") Collection<String> azioni);
}
