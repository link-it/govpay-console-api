package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "tipi_versamento")
@SequenceGenerator(name = "seq_tipi_versamento", sequenceName = "seq_tipi_versamento", allocationSize = 1)
public class TipoVersamento extends AbstractTipoVersamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tipi_versamento")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_tipo_versamento", nullable = false, length = 35)
    private String codTipoVersamento;

    @Column(name = "descrizione", nullable = false, length = 255)
    private String descrizione;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodTipoVersamento() {
        return codTipoVersamento;
    }

    public void setCodTipoVersamento(String codTipoVersamento) {
        this.codTipoVersamento = codTipoVersamento;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }
}
