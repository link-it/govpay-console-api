package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "applicazioni")
@SequenceGenerator(name = "seq_applicazioni", sequenceName = "seq_applicazioni", allocationSize = 1)
public class Applicazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_applicazioni")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_applicazione", nullable = false, length = 35)
    private String codApplicazione;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodApplicazione() {
        return codApplicazione;
    }

    public void setCodApplicazione(String codApplicazione) {
        this.codApplicazione = codApplicazione;
    }
}
