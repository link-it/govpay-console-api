package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * IBAN associabile a un singolo versamento (accredito o appoggio). Mappa
 * {@code id}, {@code postale} (l'avviso PDF lo usa per valorizzare
 * {@code postal=true} sul payload del microservizio {@code govpay-stampe}) e
 * {@code codIban} (esposto nelle voci di tipo incasso diretto).
 */
@Entity
@Table(name = "iban_accredito")
@SequenceGenerator(name = "seq_iban_accredito", sequenceName = "seq_iban_accredito", allocationSize = 1)
public class IbanAccredito {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_iban_accredito")
    @Column(name = "id")
    private Long id;

    @Column(name = "postale", nullable = false)
    private Boolean postale;

    @Column(name = "cod_iban", nullable = false, length = 255)
    private String codIban;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getPostale() {
        return postale;
    }

    public void setPostale(Boolean postale) {
        this.postale = postale;
    }

    public String getCodIban() {
        return codIban;
    }

    public void setCodIban(String codIban) {
        this.codIban = codIban;
    }
}
