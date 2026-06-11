package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    public Long getId() {
        return id;
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
