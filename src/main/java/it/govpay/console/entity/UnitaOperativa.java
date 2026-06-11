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
@Table(name = "uo")
@SequenceGenerator(name = "seq_uo", sequenceName = "seq_uo", allocationSize = 1)
public class UnitaOperativa {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_uo")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_uo", nullable = false, length = 35)
    private String codUo;

    @Column(name = "uo_denominazione", length = 70)
    private String uoDenominazione;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodUo() {
        return codUo;
    }

    public void setCodUo(String codUo) {
        this.codUo = codUo;
    }

    public String getUoDenominazione() {
        return uoDenominazione;
    }

    public void setUoDenominazione(String uoDenominazione) {
        this.uoDenominazione = uoDenominazione;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }
}
