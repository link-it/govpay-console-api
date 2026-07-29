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
 * Riga di cache degli IBAN abilitati su pagoPA per dominio, sincronizzata dal
 * batch di verifica IBAN. Tabella scritta da govpay-iban-batch, letta qui in
 * sola lettura.
 */
@Entity
@Table(name = "pagopa_iban_cache")
@SequenceGenerator(name = "seq_pagopa_iban_cache", sequenceName = "seq_pagopa_iban_cache", allocationSize = 1)
public class IbanCache {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pagopa_iban_cache")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iban", nullable = false, length = 35)
    private String iban;

    @Column(name = "attivo", nullable = false)
    private Boolean attivo;

    @Column(name = "data_ultima_verifica", nullable = false)
    private OffsetDateTime dataUltimaVerifica;

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

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public Boolean getAttivo() {
        return attivo;
    }

    public void setAttivo(Boolean attivo) {
        this.attivo = attivo;
    }

    public OffsetDateTime getDataUltimaVerifica() {
        return dataUltimaVerifica;
    }

    public void setDataUltimaVerifica(OffsetDateTime dataUltimaVerifica) {
        this.dataUltimaVerifica = dataUltimaVerifica;
    }
}
