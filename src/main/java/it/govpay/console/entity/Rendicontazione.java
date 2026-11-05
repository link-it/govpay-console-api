package it.govpay.console.entity;

import java.time.OffsetDateTime;

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
 * Riga di rendicontazione (un singolo pagamento/IUV rendicontato all'interno
 * di un {@link Fr}). Mappa la tabella {@code rendicontazioni}.
 *
 * <p>{@code fr} e' una relazione di sola lettura sulla stessa colonna
 * {@code id_fr} dello scalare {@link #idFr}.
 */
@Entity
@Table(name = "rendicontazioni")
@SequenceGenerator(name = "seq_rendicontazioni", sequenceName = "seq_rendicontazioni", allocationSize = 1)
public class Rendicontazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_rendicontazioni")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_fr", nullable = false)
    private Long idFr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fr", insertable = false, updatable = false)
    private Fr fr;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", length = 35)
    private String iur;

    @Column(name = "indice_dati")
    private Integer indiceDati;

    @Column(name = "importo_pagato")
    private Double importoPagato;

    @Column(name = "esito")
    private Integer esito;

    @Column(name = "data")
    private OffsetDateTime data;

    @Column(name = "stato", length = 35)
    private String stato;

    @Column(name = "anomalie", columnDefinition = "TEXT")
    private String anomalie;

    @Column(name = "id_pagamento")
    private Long idPagamento;

    @Column(name = "id_singolo_versamento")
    private Long idSingoloVersamento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdFr() {
        return idFr;
    }

    public void setIdFr(Long idFr) {
        this.idFr = idFr;
    }

    public Fr getFr() {
        return fr;
    }

    public void setFr(Fr fr) {
        this.fr = fr;
    }

    public String getIuv() {
        return iuv;
    }

    public void setIuv(String iuv) {
        this.iuv = iuv;
    }

    public String getIur() {
        return iur;
    }

    public void setIur(String iur) {
        this.iur = iur;
    }

    public Integer getIndiceDati() {
        return indiceDati;
    }

    public void setIndiceDati(Integer indiceDati) {
        this.indiceDati = indiceDati;
    }

    public Double getImportoPagato() {
        return importoPagato;
    }

    public void setImportoPagato(Double importoPagato) {
        this.importoPagato = importoPagato;
    }

    public Integer getEsito() {
        return esito;
    }

    public void setEsito(Integer esito) {
        this.esito = esito;
    }

    public OffsetDateTime getData() {
        return data;
    }

    public void setData(OffsetDateTime data) {
        this.data = data;
    }

    public String getStato() {
        return stato;
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public String getAnomalie() {
        return anomalie;
    }

    public void setAnomalie(String anomalie) {
        this.anomalie = anomalie;
    }

    public Long getIdPagamento() {
        return idPagamento;
    }

    public void setIdPagamento(Long idPagamento) {
        this.idPagamento = idPagamento;
    }

    public Long getIdSingoloVersamento() {
        return idSingoloVersamento;
    }

    public void setIdSingoloVersamento(Long idSingoloVersamento) {
        this.idSingoloVersamento = idSingoloVersamento;
    }
}
