package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "stazioni")
@SequenceGenerator(name = "seq_stazioni", sequenceName = "seq_stazioni", allocationSize = 1)
public class Stazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_stazioni")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_stazione", nullable = false, length = 35)
    private String codStazione;

    @Column(name = "password", nullable = false, length = 35)
    private String password;

    @Column(name = "abilitato", nullable = false)
    private Boolean abilitato;

    @Column(name = "application_code", nullable = false)
    private Integer applicationCode;

    @Column(name = "versione", nullable = false, length = 35)
    private String versione;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_intermediario", nullable = false)
    private Intermediario intermediario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodStazione() {
        return codStazione;
    }

    public void setCodStazione(String codStazione) {
        this.codStazione = codStazione;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }

    public Integer getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(Integer applicationCode) {
        this.applicationCode = applicationCode;
    }

    public String getVersione() {
        return versione;
    }

    public void setVersione(String versione) {
        this.versione = versione;
    }

    public Intermediario getIntermediario() {
        return intermediario;
    }

    public void setIntermediario(Intermediario intermediario) {
        this.intermediario = intermediario;
    }
}
