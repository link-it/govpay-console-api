package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "intermediari")
@SequenceGenerator(name = "seq_intermediari", sequenceName = "seq_intermediari", allocationSize = 1)
public class Intermediario {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_intermediari")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_intermediario", nullable = false, length = 35)
    private String codIntermediario;

    @Column(name = "denominazione", nullable = false, length = 255)
    private String denominazione;

    @Column(name = "principal", nullable = false, length = 4000)
    private String principal;

    @Column(name = "principal_originale", nullable = false, length = 4000)
    private String principalOriginale;

    @Column(name = "cod_connettore_pdd", nullable = false, length = 35)
    private String codConnettorePdd;

    @Column(name = "abilitato", nullable = false)
    private Boolean abilitato;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodIntermediario() {
        return codIntermediario;
    }

    public void setCodIntermediario(String codIntermediario) {
        this.codIntermediario = codIntermediario;
    }

    public String getDenominazione() {
        return denominazione;
    }

    public void setDenominazione(String denominazione) {
        this.denominazione = denominazione;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getPrincipalOriginale() {
        return principalOriginale;
    }

    public void setPrincipalOriginale(String principalOriginale) {
        this.principalOriginale = principalOriginale;
    }

    public String getCodConnettorePdd() {
        return codConnettorePdd;
    }

    public void setCodConnettorePdd(String codConnettorePdd) {
        this.codConnettorePdd = codConnettorePdd;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }
}
