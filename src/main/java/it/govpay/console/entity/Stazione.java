package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "application_code", nullable = false)
    private Integer applicationCode;

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

    public Integer getApplicationCode() {
        return applicationCode;
    }

    public void setApplicationCode(Integer applicationCode) {
        this.applicationCode = applicationCode;
    }
}
