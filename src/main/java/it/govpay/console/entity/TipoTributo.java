package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Tipo di tributo (entrata). Mappato slim: serve solo {@code codTributo}
 * (= codice entrata) esposto nelle voci di tipo {@code ENTRATA_ANAGRAFICA}.
 */
@Entity
@Table(name = "tipi_tributo")
@SequenceGenerator(name = "seq_tipi_tributo", sequenceName = "seq_tipi_tributo", allocationSize = 1)
public class TipoTributo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tipi_tributo")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_tributo", nullable = false, length = 255)
    private String codTributo;

    @Column(name = "descrizione", length = 255)
    private String descrizione;

    /**
     * Codifica del tipo contabilita' come singola cifra ({@code 0..9}), allineata
     * alla colonna {@code tipo_contabilita VARCHAR(1)}. La conversione verso/da i
     * nomi enum esposti dall'API e' a carico del mapper.
     */
    @Column(name = "tipo_contabilita", length = 1)
    private String tipoContabilita;

    @Column(name = "cod_contabilita", length = 255)
    private String codContabilita;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodTributo() {
        return codTributo;
    }

    public void setCodTributo(String codTributo) {
        this.codTributo = codTributo;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getTipoContabilita() {
        return tipoContabilita;
    }

    public void setTipoContabilita(String tipoContabilita) {
        this.tipoContabilita = tipoContabilita;
    }

    public String getCodContabilita() {
        return codContabilita;
    }

    public void setCodContabilita(String codContabilita) {
        this.codContabilita = codContabilita;
    }
}
