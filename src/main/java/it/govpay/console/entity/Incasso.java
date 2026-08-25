package it.govpay.console.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Riconciliazione (V1: incasso) pagoPA. Mappa la tabella {@code incassi} con i
 * soli campi usati dalla consultazione {@code GET /riconciliazioni} (lista +
 * dettaglio): niente {@code nome_dispositivo}/{@code id_applicazione}/
 * {@code id_operatore}, non esposti da nessuna API.
 *
 * <p>A differenza di {@link Fr}, questa tabella non ha una FK numerica verso
 * {@code domini}: solo {@code cod_dominio} (stringa). Il filtro ACL non puo'
 * quindi usare {@code DominioVisibilita.predicate} (che lavora su una FK
 * {@code Long}): vedi {@code IncassoSpecifications.visibiliPerOperatore}, che
 * riusa {@link it.govpay.console.eventi.EventoAcl} per risolvere i codici
 * dominio visibili come lista di stringhe.
 */
@Entity
@Table(name = "incassi")
@SequenceGenerator(name = "seq_incassi", sequenceName = "seq_incassi", allocationSize = 1)
public class Incasso {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_incassi")
    @Column(name = "id")
    private Long id;

    @Column(name = "identificativo", nullable = false, length = 35)
    private String identificativo;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "trn", nullable = false, length = 35)
    private String trn;

    @Column(name = "causale", length = 512)
    private String causale;

    @Column(name = "importo", nullable = false)
    private Double importo;

    @Column(name = "data_valuta")
    private LocalDate dataValuta;

    @Column(name = "data_contabile")
    private LocalDate dataContabile;

    @Column(name = "data_ora_incasso", nullable = false)
    private OffsetDateTime dataOraIncasso;

    @Column(name = "iban_accredito", length = 35)
    private String ibanAccredito;

    @Column(name = "sct", length = 35)
    private String sct;

    @Column(name = "iuv", length = 35)
    private String iuv;

    @Column(name = "cod_flusso_rendicontazione", length = 35)
    private String codFlussoRendicontazione;

    @Column(name = "stato", nullable = false, length = 35)
    private String stato;

    @Column(name = "descrizione_stato", length = 255)
    private String descrizioneStato;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentificativo() {
        return identificativo;
    }

    public void setIdentificativo(String identificativo) {
        this.identificativo = identificativo;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
    }

    public String getTrn() {
        return trn;
    }

    public void setTrn(String trn) {
        this.trn = trn;
    }

    public String getCausale() {
        return causale;
    }

    public void setCausale(String causale) {
        this.causale = causale;
    }

    public Double getImporto() {
        return importo;
    }

    public void setImporto(Double importo) {
        this.importo = importo;
    }

    public LocalDate getDataValuta() {
        return dataValuta;
    }

    public void setDataValuta(LocalDate dataValuta) {
        this.dataValuta = dataValuta;
    }

    public LocalDate getDataContabile() {
        return dataContabile;
    }

    public void setDataContabile(LocalDate dataContabile) {
        this.dataContabile = dataContabile;
    }

    public OffsetDateTime getDataOraIncasso() {
        return dataOraIncasso;
    }

    public void setDataOraIncasso(OffsetDateTime dataOraIncasso) {
        this.dataOraIncasso = dataOraIncasso;
    }

    public String getIbanAccredito() {
        return ibanAccredito;
    }

    public void setIbanAccredito(String ibanAccredito) {
        this.ibanAccredito = ibanAccredito;
    }

    public String getSct() {
        return sct;
    }

    public void setSct(String sct) {
        this.sct = sct;
    }

    public String getIuv() {
        return iuv;
    }

    public void setIuv(String iuv) {
        this.iuv = iuv;
    }

    public String getCodFlussoRendicontazione() {
        return codFlussoRendicontazione;
    }

    public void setCodFlussoRendicontazione(String codFlussoRendicontazione) {
        this.codFlussoRendicontazione = codFlussoRendicontazione;
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
}
