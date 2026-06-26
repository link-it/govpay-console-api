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
 * Configurazione per-dominio di un {@link TipoVersamento}. In V1 il converter
 * popola il {@code TipoPendenzaRef} via questo livello (non direttamente dal
 * {@link TipoVersamento} globale), per ereditare gli eventuali override
 * specifici del dominio.
 */
@Entity
@Table(name = "tipi_vers_domini")
@SequenceGenerator(name = "seq_tipi_vers_domini", sequenceName = "seq_tipi_vers_domini", allocationSize = 1)
public class TipoVersamentoDominio {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tipi_vers_domini")
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_versamento", nullable = false)
    private TipoVersamento tipoVersamento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }

    public TipoVersamento getTipoVersamento() {
        return tipoVersamento;
    }

    public void setTipoVersamento(TipoVersamento tipoVersamento) {
        this.tipoVersamento = tipoVersamento;
    }
}
