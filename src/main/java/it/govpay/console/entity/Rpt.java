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
 * RPT/RT (Richiesta Pagamento Telematico + Ricevuta Telematica) PagoPA.
 * Mappa la tabella {@code rpt} di V1 con i soli campi usati dall'endpoint
 * {@code /pendenze/{idA2A}/{idPendenza}/ricevuta}:
 * <ul>
 *   <li>chiavi pagoPA: {@code iuv}, {@code ccp}, {@code codDominio};</li>
 *   <li>tracciati originali: {@code xmlRpt} e {@code xmlRt} (serviti su
 *       {@code application/xml} o convertiti in JSON);</li>
 *   <li>esito e ammontare: {@code codEsitoPagamento}, {@code importoTotalePagato};</li>
 *   <li>stato della transazione: {@code stato}, {@code descrizioneStato};</li>
 *   <li>date: {@code dataMsgRicevuta}, {@code dataMsgRichiesta};</li>
 *   <li>identificativi PSP / istituto attestante;</li>
 *   <li>versione SANP per il dispatch di conversione e il payload PDF.</li>
 * </ul>
 */
@Entity
@Table(name = "rpt")
@SequenceGenerator(name = "seq_rpt", sequenceName = "seq_rpt", allocationSize = 1)
public class Rpt {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_rpt")
    @Column(name = "id")
    private Long id;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "ccp", nullable = false, length = 35)
    private String ccp;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "xml_rpt", columnDefinition = "BYTEA")
    private byte[] xmlRpt;

    @Column(name = "xml_rt", columnDefinition = "BYTEA")
    private byte[] xmlRt;

    @Column(name = "stato", nullable = false, length = 35)
    private String stato;

    @Column(name = "descrizione_stato", columnDefinition = "TEXT")
    private String descrizioneStato;

    @Column(name = "cod_esito_pagamento")
    private Integer codEsitoPagamento;

    @Column(name = "importo_totale_pagato")
    private Double importoTotalePagato;

    @Column(name = "data_msg_richiesta", nullable = false)
    private OffsetDateTime dataMsgRichiesta;

    @Column(name = "data_msg_ricevuta")
    private OffsetDateTime dataMsgRicevuta;

    @Column(name = "cod_msg_ricevuta", length = 35)
    private String codMsgRicevuta;

    @Column(name = "cod_psp", length = 35)
    private String codPsp;

    @Column(name = "denominazione_attestante", length = 70)
    private String denominazioneAttestante;

    @Column(name = "identificativo_attestante", length = 35)
    private String identificativoAttestante;

    @Column(name = "cod_transazione_rt", length = 36)
    private String codTransazioneRt;

    @Column(name = "versione", nullable = false, length = 35)
    private String versione;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_versamento", nullable = false)
    private Versamento versamento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIuv() {
        return iuv;
    }

    public void setIuv(String iuv) {
        this.iuv = iuv;
    }

    public String getCcp() {
        return ccp;
    }

    public void setCcp(String ccp) {
        this.ccp = ccp;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
    }

    public byte[] getXmlRpt() {
        return xmlRpt;
    }

    public void setXmlRpt(byte[] xmlRpt) {
        this.xmlRpt = xmlRpt;
    }

    public byte[] getXmlRt() {
        return xmlRt;
    }

    public void setXmlRt(byte[] xmlRt) {
        this.xmlRt = xmlRt;
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

    public Integer getCodEsitoPagamento() {
        return codEsitoPagamento;
    }

    public void setCodEsitoPagamento(Integer codEsitoPagamento) {
        this.codEsitoPagamento = codEsitoPagamento;
    }

    public Double getImportoTotalePagato() {
        return importoTotalePagato;
    }

    public void setImportoTotalePagato(Double importoTotalePagato) {
        this.importoTotalePagato = importoTotalePagato;
    }

    public OffsetDateTime getDataMsgRichiesta() {
        return dataMsgRichiesta;
    }

    public void setDataMsgRichiesta(OffsetDateTime dataMsgRichiesta) {
        this.dataMsgRichiesta = dataMsgRichiesta;
    }

    public OffsetDateTime getDataMsgRicevuta() {
        return dataMsgRicevuta;
    }

    public void setDataMsgRicevuta(OffsetDateTime dataMsgRicevuta) {
        this.dataMsgRicevuta = dataMsgRicevuta;
    }

    public String getCodMsgRicevuta() {
        return codMsgRicevuta;
    }

    public void setCodMsgRicevuta(String codMsgRicevuta) {
        this.codMsgRicevuta = codMsgRicevuta;
    }

    public String getCodPsp() {
        return codPsp;
    }

    public void setCodPsp(String codPsp) {
        this.codPsp = codPsp;
    }

    public String getDenominazioneAttestante() {
        return denominazioneAttestante;
    }

    public void setDenominazioneAttestante(String denominazioneAttestante) {
        this.denominazioneAttestante = denominazioneAttestante;
    }

    public String getIdentificativoAttestante() {
        return identificativoAttestante;
    }

    public void setIdentificativoAttestante(String identificativoAttestante) {
        this.identificativoAttestante = identificativoAttestante;
    }

    public String getCodTransazioneRt() {
        return codTransazioneRt;
    }

    public void setCodTransazioneRt(String codTransazioneRt) {
        this.codTransazioneRt = codTransazioneRt;
    }

    public String getVersione() {
        return versione;
    }

    public void setVersione(String versione) {
        this.versione = versione;
    }

    public Versamento getVersamento() {
        return versamento;
    }

    public void setVersamento(Versamento versamento) {
        this.versamento = versamento;
    }
}
