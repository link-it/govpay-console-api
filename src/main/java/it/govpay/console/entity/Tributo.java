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

/**
 * Entrata configurata per un dominio. Mappato slim: serve solo per risalire al
 * {@link TipoTributo} (e quindi al codice entrata) di una voce di pendenza.
 */
@Entity
@Table(name = "tributi")
@SequenceGenerator(name = "seq_tributi", sequenceName = "seq_tributi", allocationSize = 1)
public class Tributo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tributi")
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_tributo", nullable = false)
    private TipoTributo tipoTributo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoTributo getTipoTributo() {
        return tipoTributo;
    }

    public void setTipoTributo(TipoTributo tipoTributo) {
        this.tipoTributo = tipoTributo;
    }
}
