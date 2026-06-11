package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Associazione utenza ↔ dominio (eventualmente ↔ uo). Quando una utenza
 * ha {@code autorizzazione_domini_star = false}, l'elenco dei domini visibili
 * proviene da qui.
 */
@Entity
@Table(name = "utenze_domini")
@SequenceGenerator(name = "seq_utenze_domini", sequenceName = "seq_utenze_domini", allocationSize = 1)
public class UtenzaDominio {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_utenze_domini")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_utenza", nullable = false)
    private Long idUtenza;

    @Column(name = "id_dominio")
    private Long idDominio;

    @Column(name = "id_uo")
    private Long idUo;

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

    public Long getIdDominio() {
        return idDominio;
    }

    public void setIdDominio(Long idDominio) {
        this.idDominio = idDominio;
    }

    public Long getIdUo() {
        return idUo;
    }

    public void setIdUo(Long idUo) {
        this.idUo = idUo;
    }
}
