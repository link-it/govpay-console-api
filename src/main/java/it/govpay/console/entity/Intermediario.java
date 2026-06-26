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

    @Column(name = "cod_connettore_recupero_rt", length = 35)
    private String codConnettoreRecuperoRt;

    @Column(name = "cod_connettore_aca", length = 35)
    private String codConnettoreAca;

    @Column(name = "cod_connettore_gpd", length = 35)
    private String codConnettoreGpd;

    @Column(name = "cod_connettore_fr", length = 35)
    private String codConnettoreFr;

    @Column(name = "cod_connettore_backoffice_ec", length = 35)
    private String codConnettoreBackofficeEc;

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

    public String getCodConnettoreRecuperoRt() {
        return codConnettoreRecuperoRt;
    }

    public void setCodConnettoreRecuperoRt(String codConnettoreRecuperoRt) {
        this.codConnettoreRecuperoRt = codConnettoreRecuperoRt;
    }

    public String getCodConnettoreAca() {
        return codConnettoreAca;
    }

    public void setCodConnettoreAca(String codConnettoreAca) {
        this.codConnettoreAca = codConnettoreAca;
    }

    public String getCodConnettoreGpd() {
        return codConnettoreGpd;
    }

    public void setCodConnettoreGpd(String codConnettoreGpd) {
        this.codConnettoreGpd = codConnettoreGpd;
    }

    public String getCodConnettoreFr() {
        return codConnettoreFr;
    }

    public void setCodConnettoreFr(String codConnettoreFr) {
        this.codConnettoreFr = codConnettoreFr;
    }

    public String getCodConnettoreBackofficeEc() {
        return codConnettoreBackofficeEc;
    }

    public void setCodConnettoreBackofficeEc(String codConnettoreBackofficeEc) {
        this.codConnettoreBackofficeEc = codConnettoreBackofficeEc;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }
}
