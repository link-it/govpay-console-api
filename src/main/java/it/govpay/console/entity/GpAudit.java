package it.govpay.console.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Audit GovPay (tabella `gp_audit`).
 */
@Entity
@Table(name = "gp_audit")
@SequenceGenerator(name = "seq_gp_audit", sequenceName = "seq_gp_audit", allocationSize = 1)
public class GpAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_gp_audit")
    @Column(name = "id")
    private Long id;

    @Column(name = "data", nullable = false)
    private OffsetDateTime data;

    @Column(name = "id_oggetto", nullable = false)
    private Long idOggetto;

    @Column(name = "tipo_oggetto", nullable = false, length = 255)
    private String tipoOggetto;

    @Column(name = "oggetto", nullable = false, columnDefinition = "TEXT")
    private String oggetto;

    @Column(name = "id_operatore", nullable = false)
    private Long idOperatore;

    @Column(name = "ip_richiedente", length = 45)
    private String ipRichiedente;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getData() {
        return data;
    }

    public void setData(OffsetDateTime data) {
        this.data = data;
    }

    public Long getIdOggetto() {
        return idOggetto;
    }

    public void setIdOggetto(Long idOggetto) {
        this.idOggetto = idOggetto;
    }

    public String getTipoOggetto() {
        return tipoOggetto;
    }

    public void setTipoOggetto(String tipoOggetto) {
        this.tipoOggetto = tipoOggetto;
    }

    public String getOggetto() {
        return oggetto;
    }

    public void setOggetto(String oggetto) {
        this.oggetto = oggetto;
    }

    public Long getIdOperatore() {
        return idOperatore;
    }

    public void setIdOperatore(Long idOperatore) {
        this.idOperatore = idOperatore;
    }

    public String getIpRichiedente() {
        return ipRichiedente;
    }

    public void setIpRichiedente(String ipRichiedente) {
        this.ipRichiedente = ipRichiedente;
    }
}
