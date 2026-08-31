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
 * Riga di cache dell'anagrafica di un Ente Creditore sincronizzata da pagoPA.
 * Tabella scritta dal batch govpay-iban-batch, letta qui in sola lettura.
 */
@Entity
@Table(name = "pagopa_ec_cache")
@SequenceGenerator(name = "seq_pagopa_ec_cache", sequenceName = "seq_pagopa_ec_cache", allocationSize = 1)
public class EnteCreditoreCache {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pagopa_ec_cache")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_fiscale", nullable = false, length = 16)
    private String codFiscale;

    @Column(name = "denominazione", nullable = false, length = 255)
    private String denominazione;

    @Column(name = "station_id", length = 35)
    private String stationId;

    @Column(name = "aux_digit", nullable = false, length = 2)
    private String auxDigit;

    @Column(name = "segregation_code", length = 4)
    private String segregationCode;

    @Column(name = "cbill_code", length = 35)
    private String cbillCode;

    @Column(name = "data_ultimo_aggiornamento", nullable = false)
    private OffsetDateTime dataUltimoAggiornamento;

    @Column(name = "cod_intermediario", length = 35)
    private String codIntermediario;

    @Column(name = "check_stato", length = 35)
    private String checkStato;

    @Column(name = "check_motivo", length = 1024)
    private String checkMotivo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodFiscale() {
        return codFiscale;
    }

    public void setCodFiscale(String codFiscale) {
        this.codFiscale = codFiscale;
    }

    public String getDenominazione() {
        return denominazione;
    }

    public void setDenominazione(String denominazione) {
        this.denominazione = denominazione;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getAuxDigit() {
        return auxDigit;
    }

    public void setAuxDigit(String auxDigit) {
        this.auxDigit = auxDigit;
    }

    public String getSegregationCode() {
        return segregationCode;
    }

    public void setSegregationCode(String segregationCode) {
        this.segregationCode = segregationCode;
    }

    public String getCbillCode() {
        return cbillCode;
    }

    public void setCbillCode(String cbillCode) {
        this.cbillCode = cbillCode;
    }

    public OffsetDateTime getDataUltimoAggiornamento() {
        return dataUltimoAggiornamento;
    }

    public void setDataUltimoAggiornamento(OffsetDateTime dataUltimoAggiornamento) {
        this.dataUltimoAggiornamento = dataUltimoAggiornamento;
    }

    public String getCodIntermediario() {
        return codIntermediario;
    }

    public void setCodIntermediario(String codIntermediario) {
        this.codIntermediario = codIntermediario;
    }

    public String getCheckStato() {
        return checkStato;
    }

    public void setCheckStato(String checkStato) {
        this.checkStato = checkStato;
    }

    public String getCheckMotivo() {
        return checkMotivo;
    }

    public void setCheckMotivo(String checkMotivo) {
        this.checkMotivo = checkMotivo;
    }
}
