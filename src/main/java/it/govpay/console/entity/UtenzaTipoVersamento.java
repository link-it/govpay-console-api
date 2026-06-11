package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Associazione utenza ↔ tipo versamento. Quando una utenza ha
 * {@code autorizzazione_tipi_vers_star = false}, l'elenco dei tipi
 * versamento visibili proviene da qui.
 */
@Entity
@Table(name = "utenze_tipo_vers")
@SequenceGenerator(name = "seq_utenze_tipo_vers", sequenceName = "seq_utenze_tipo_vers", allocationSize = 1)
public class UtenzaTipoVersamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_utenze_tipo_vers")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_utenza", nullable = false)
    private Long idUtenza;

    @Column(name = "id_tipo_versamento", nullable = false)
    private Long idTipoVersamento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdUtenza() {
        return idUtenza;
    }

    public void setIdUtenza(Long idUtenza) {
        this.idUtenza = idUtenza;
    }

    public Long getIdTipoVersamento() {
        return idTipoVersamento;
    }

    public void setIdTipoVersamento(Long idTipoVersamento) {
        this.idTipoVersamento = idTipoVersamento;
    }
}
