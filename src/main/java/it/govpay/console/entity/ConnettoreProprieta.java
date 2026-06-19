package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una singola proprieta' di un connettore. La tabella {@code connettori} e' un
 * key-value store: un connettore logico e' l'insieme delle righe che condividono
 * lo stesso {@code cod_connettore}, ciascuna una coppia {@code (cod_proprieta, valore)}.
 */
@Entity
@Table(name = "connettori",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cod_connettore", "cod_proprieta"}))
@SequenceGenerator(name = "seq_connettori", sequenceName = "seq_connettori", allocationSize = 1)
public class ConnettoreProprieta {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_connettori")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_connettore", nullable = false, length = 255)
    private String codConnettore;

    @Column(name = "cod_proprieta", nullable = false, length = 255)
    private String codProprieta;

    @Column(name = "valore", nullable = false, length = 255)
    private String valore;

    public ConnettoreProprieta() {
    }

    public ConnettoreProprieta(String codConnettore, String codProprieta, String valore) {
        this.codConnettore = codConnettore;
        this.codProprieta = codProprieta;
        this.valore = valore;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodConnettore() {
        return codConnettore;
    }

    public void setCodConnettore(String codConnettore) {
        this.codConnettore = codConnettore;
    }

    public String getCodProprieta() {
        return codProprieta;
    }

    public void setCodProprieta(String codProprieta) {
        this.codProprieta = codProprieta;
    }

    public String getValore() {
        return valore;
    }

    public void setValore(String valore) {
        this.valore = valore;
    }
}
