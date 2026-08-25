package it.govpay.console.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Pagamento riscosso pagoPA. Mappa la tabella {@code pagamenti} con i soli
 * campi usati da {@code riscossioni[]} nel dettaglio riconciliazione
 * ({@code GET /riconciliazioni/{idDominio}/{id}}). {@code allegato} (BYTEA) e
 * i campi di revoca non sono mai selezionati: fuori scope, ed evita di
 * caricare un blob potenzialmente pesante su ogni query.
 */
@Entity
@Table(name = "pagamenti")
@SequenceGenerator(name = "seq_pagamenti", sequenceName = "seq_pagamenti", allocationSize = 1)
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pagamenti")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "indice_dati", nullable = false)
    private Integer indiceDati;

    @Column(name = "importo_pagato", nullable = false)
    private Double importoPagato;

    @Column(name = "data_pagamento", nullable = false)
    private OffsetDateTime dataPagamento;

    @Column(name = "commissioni_psp")
    private Double commissioniPsp;

    @Column(name = "stato", length = 35)
    private String stato;

    @Column(name = "tipo", nullable = false, length = 35)
    private String tipo;

    @Column(name = "id_rpt")
    private Long idRpt;

    @Column(name = "id_singolo_versamento")
    private Long idSingoloVersamento;

    @Column(name = "id_incasso")
    private Long idIncasso;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
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

    public OffsetDateTime getDataPagamento() {
        return dataPagamento;
    }

    public void setDataPagamento(OffsetDateTime dataPagamento) {
        this.dataPagamento = dataPagamento;
    }

    public Double getCommissioniPsp() {
        return commissioniPsp;
    }

    public void setCommissioniPsp(Double commissioniPsp) {
        this.commissioniPsp = commissioniPsp;
    }

    public String getStato() {
        return stato;
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getIdRpt() {
        return idRpt;
    }

    public void setIdRpt(Long idRpt) {
        this.idRpt = idRpt;
    }

    public Long getIdSingoloVersamento() {
        return idSingoloVersamento;
    }

    public void setIdSingoloVersamento(Long idSingoloVersamento) {
        this.idSingoloVersamento = idSingoloVersamento;
    }

    public Long getIdIncasso() {
        return idIncasso;
    }

    public void setIdIncasso(Long idIncasso) {
        this.idIncasso = idIncasso;
    }
}
