package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "operatori")
@SequenceGenerator(name = "seq_operatori", sequenceName = "seq_operatori", allocationSize = 1)
public class Operatore {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_operatori")
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false, length = 35)
    private String nome;

    @Column(name = "id_utenza", nullable = false)
    private Long idUtenza;

    // Relazione read-only verso la utenza (la colonna id_utenza resta gestita
    // dal campo idUtenza scrivibile): serve per filtrare/ordinare gli operatori
    // per principal/abilitato senza rompere il codice esistente che valorizza
    // solo idUtenza.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_utenza", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Utenza utenza;

    public Long getId() {
        return id;
    }

    public Utenza getUtenza() {
        return utenza;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Long getIdUtenza() {
        return idUtenza;
    }

    public void setIdUtenza(Long idUtenza) {
        this.idUtenza = idUtenza;
    }
}
