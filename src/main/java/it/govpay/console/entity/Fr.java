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
 * Flusso di rendicontazione (FR) pagoPA. Mappa la tabella {@code fr} con i
 * soli campi usati dalla consultazione {@code GET /flussi-rendicontazione}
 * (lista): niente {@code xml} qui (blob, caricato solo nel dettaglio).
 *
 * <p>{@code idDominio} e' la FK tecnica ({@code id_dominio}), usata solo per
 * il filtro ACL; il codice dominio esposto in API e' {@code codDominio}.
 */
@Entity
@Table(name = "fr")
@SequenceGenerator(name = "seq_fr", sequenceName = "seq_fr", allocationSize = 1)
public class Fr {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_fr")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_dominio", nullable = false)
    private Long idDominio;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "cod_flusso", nullable = false, length = 35)
    private String codFlusso;

    @Column(name = "cod_psp", nullable = false, length = 35)
    private String codPsp;

    @Column(name = "revisione")
    private Long revisione;

    @Column(name = "stato", nullable = false, length = 35)
    private String stato;

    @Column(name = "descrizione_stato", columnDefinition = "TEXT")
    private String descrizioneStato;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "data_ora_flusso", nullable = false)
    private OffsetDateTime dataOraFlusso;

    @Column(name = "data_regolamento")
    private OffsetDateTime dataRegolamento;

    @Column(name = "data_acquisizione", nullable = false)
    private OffsetDateTime dataAcquisizione;

    @Column(name = "numero_pagamenti")
    private Long numeroPagamenti;

    @Column(name = "importo_totale_pagamenti")
    private Double importoTotalePagamenti;

    @Column(name = "obsoleto", nullable = false)
    private boolean obsoleto;

    @Column(name = "id_incasso")
    private Long idIncasso;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdDominio() {
        return idDominio;
    }

    public void setIdDominio(Long idDominio) {
        this.idDominio = idDominio;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
    }

    public String getCodFlusso() {
        return codFlusso;
    }

    public void setCodFlusso(String codFlusso) {
        this.codFlusso = codFlusso;
    }

    public String getCodPsp() {
        return codPsp;
    }

    public void setCodPsp(String codPsp) {
        this.codPsp = codPsp;
    }

    public Long getRevisione() {
        return revisione;
    }

    public void setRevisione(Long revisione) {
        this.revisione = revisione;
    }

    public String getStato() {
        return stato;
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public String getDescrizioneStato() {
        return descrizioneStato;
    }

    public void setDescrizioneStato(String descrizioneStato) {
        this.descrizioneStato = descrizioneStato;
    }

    public String getIur() {
        return iur;
    }

    public void setIur(String iur) {
        this.iur = iur;
    }

    public OffsetDateTime getDataOraFlusso() {
        return dataOraFlusso;
    }

    public void setDataOraFlusso(OffsetDateTime dataOraFlusso) {
        this.dataOraFlusso = dataOraFlusso;
    }

    public OffsetDateTime getDataRegolamento() {
        return dataRegolamento;
    }

    public void setDataRegolamento(OffsetDateTime dataRegolamento) {
        this.dataRegolamento = dataRegolamento;
    }

    public OffsetDateTime getDataAcquisizione() {
        return dataAcquisizione;
    }

    public void setDataAcquisizione(OffsetDateTime dataAcquisizione) {
        this.dataAcquisizione = dataAcquisizione;
    }

    public Long getNumeroPagamenti() {
        return numeroPagamenti;
    }

    public void setNumeroPagamenti(Long numeroPagamenti) {
        this.numeroPagamenti = numeroPagamenti;
    }

    public Double getImportoTotalePagamenti() {
        return importoTotalePagamenti;
    }

    public void setImportoTotalePagamenti(Double importoTotalePagamenti) {
        this.importoTotalePagamenti = importoTotalePagamenti;
    }

    public boolean isObsoleto() {
        return obsoleto;
    }

    public void setObsoleto(boolean obsoleto) {
        this.obsoleto = obsoleto;
    }

    public Long getIdIncasso() {
        return idIncasso;
    }

    public void setIdIncasso(Long idIncasso) {
        this.idIncasso = idIncasso;
    }
}
