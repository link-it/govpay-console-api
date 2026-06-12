package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * IBAN associabile a un singolo versamento (accredito o appoggio). In V2 slim
 * mappiamo solo {@code id} e {@code postale}: serve all'avviso PDF per
 * decidere se valorizzare {@code postal=true} sul payload del microservizio
 * {@code govpay-stampe} (V1 fa lo stesso controllo in
 * {@code AvvisoPagamentoV2Utils.java:441-446}).
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
}
