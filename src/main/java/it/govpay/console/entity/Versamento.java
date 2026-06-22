package it.govpay.console.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Versamento (= "pendenza" nel dominio V2). Slim: solo i campi usati dagli
 * endpoint correnti. Aggiungere altri campi qui quando entreranno in gioco.
 */
@Entity
@Table(name = "versamenti")
@SequenceGenerator(name = "seq_versamenti", sequenceName = "seq_versamenti", allocationSize = 1)
public class Versamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_versamenti")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_versamento_ente", nullable = false, length = 35)
    private String codVersamentoEnte;

    @Column(name = "importo_totale", nullable = false)
    private Double importoTotale;

    @Column(name = "stato_versamento", nullable = false, length = 35)
    private String statoVersamento;

    @Column(name = "data_creazione", nullable = false)
    private OffsetDateTime dataCreazione;

    @Column(name = "data_validita")
    private OffsetDateTime dataValidita;

    @Column(name = "data_scadenza")
    private OffsetDateTime dataScadenza;

    @Column(name = "data_ora_ultimo_aggiornamento", nullable = false)
    private OffsetDateTime dataOraUltimoAggiornamento;

    @Column(name = "data_ultima_modifica_aca")
    private OffsetDateTime dataUltimaModificaAca;

    @Column(name = "data_ultima_comunicazione_aca")
    private OffsetDateTime dataUltimaComunicazioneAca;

    @Column(name = "causale_versamento", length = 1024)
    private String causaleVersamento;

    @Column(name = "debitore_tipo", length = 1)
    private String debitoreTipo;

    @Column(name = "debitore_identificativo", nullable = false, length = 35)
    private String debitoreIdentificativo;

    @Column(name = "debitore_anagrafica", nullable = false, length = 70)
    private String debitoreAnagrafica;

    @Column(name = "debitore_indirizzo", length = 70)
    private String debitoreIndirizzo;

    @Column(name = "debitore_civico", length = 16)
    private String debitoreCivico;

    @Column(name = "debitore_cap", length = 16)
    private String debitoreCap;

    @Column(name = "debitore_localita", length = 35)
    private String debitoreLocalita;

    @Column(name = "debitore_provincia", length = 35)
    private String debitoreProvincia;

    @Column(name = "debitore_nazione", length = 2)
    private String debitoreNazione;

    @Column(name = "debitore_email", length = 256)
    private String debitoreEmail;

    @Column(name = "debitore_cellulare", length = 35)
    private String debitoreCellulare;

    @Column(name = "tassonomia", length = 35)
    private String tassonomia;

    @Column(name = "tassonomia_avviso", length = 35)
    private String tassonomiaAvviso;

    @Column(name = "dati_allegati", columnDefinition = "TEXT")
    private String datiAllegati;

    @Column(name = "iuv_versamento", length = 35)
    private String iuvVersamento;

    @Column(name = "numero_avviso", length = 35)
    private String numeroAvviso;

    @Column(name = "anomalo", nullable = false)
    private Boolean anomalo;

    @Column(name = "ack", nullable = false)
    private Boolean ack;

    @Column(name = "divisione", length = 35)
    private String divisione;

    @Column(name = "direzione", length = 35)
    private String direzione;

    @Column(name = "importo_pagato", nullable = false)
    private Double importoPagato;

    @Column(name = "src_debitore_identificativo", nullable = false, length = 35)
    private String srcDebitoreIdentificativo;

    @Column(name = "tipo", nullable = false, length = 35)
    private String tipo;

    @Column(name = "proprieta", columnDefinition = "TEXT")
    private String proprieta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_uo")
    private UnitaOperativa unitaOperativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_applicazione", nullable = false)
    private Applicazione applicazione;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_versamento", nullable = false)
    private TipoVersamento tipoVersamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_versamento_dominio", nullable = false)
    private TipoVersamentoDominio tipoVersamentoDominio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_documento")
    private Documento documento;

    @Column(name = "cod_rata", length = 35)
    private String codRata;

    @OneToMany(mappedBy = "versamento", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("indiceDati ASC")
    private List<SingoloVersamento> singoliVersamenti = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodVersamentoEnte() {
        return codVersamentoEnte;
    }

    public void setCodVersamentoEnte(String codVersamentoEnte) {
        this.codVersamentoEnte = codVersamentoEnte;
    }

    public Double getImportoTotale() {
        return importoTotale;
    }

    public void setImportoTotale(Double importoTotale) {
        this.importoTotale = importoTotale;
    }

    public String getStatoVersamento() {
        return statoVersamento;
    }

    public void setStatoVersamento(String statoVersamento) {
        this.statoVersamento = statoVersamento;
    }

    public OffsetDateTime getDataCreazione() {
        return dataCreazione;
    }

    public void setDataCreazione(OffsetDateTime dataCreazione) {
        this.dataCreazione = dataCreazione;
    }

    public OffsetDateTime getDataValidita() {
        return dataValidita;
    }

    public void setDataValidita(OffsetDateTime dataValidita) {
        this.dataValidita = dataValidita;
    }

    public OffsetDateTime getDataUltimaModificaAca() {
        return dataUltimaModificaAca;
    }

    public void setDataUltimaModificaAca(OffsetDateTime dataUltimaModificaAca) {
        this.dataUltimaModificaAca = dataUltimaModificaAca;
    }

    public OffsetDateTime getDataUltimaComunicazioneAca() {
        return dataUltimaComunicazioneAca;
    }

    public void setDataUltimaComunicazioneAca(OffsetDateTime dataUltimaComunicazioneAca) {
        this.dataUltimaComunicazioneAca = dataUltimaComunicazioneAca;
    }

    public OffsetDateTime getDataScadenza() {
        return dataScadenza;
    }

    public void setDataScadenza(OffsetDateTime dataScadenza) {
        this.dataScadenza = dataScadenza;
    }

    public OffsetDateTime getDataOraUltimoAggiornamento() {
        return dataOraUltimoAggiornamento;
    }

    public void setDataOraUltimoAggiornamento(OffsetDateTime dataOraUltimoAggiornamento) {
        this.dataOraUltimoAggiornamento = dataOraUltimoAggiornamento;
    }

    public String getCausaleVersamento() {
        return causaleVersamento;
    }

    public void setCausaleVersamento(String causaleVersamento) {
        this.causaleVersamento = causaleVersamento;
    }

    public String getDebitoreTipo() {
        return debitoreTipo;
    }

    public void setDebitoreTipo(String debitoreTipo) {
        this.debitoreTipo = debitoreTipo;
    }

    public String getDebitoreIdentificativo() {
        return debitoreIdentificativo;
    }

    public void setDebitoreIdentificativo(String debitoreIdentificativo) {
        this.debitoreIdentificativo = debitoreIdentificativo;
    }

    public String getDebitoreAnagrafica() {
        return debitoreAnagrafica;
    }

    public void setDebitoreAnagrafica(String debitoreAnagrafica) {
        this.debitoreAnagrafica = debitoreAnagrafica;
    }

    public String getDebitoreIndirizzo() {
        return debitoreIndirizzo;
    }

    public void setDebitoreIndirizzo(String debitoreIndirizzo) {
        this.debitoreIndirizzo = debitoreIndirizzo;
    }

    public String getDebitoreCivico() {
        return debitoreCivico;
    }

    public void setDebitoreCivico(String debitoreCivico) {
        this.debitoreCivico = debitoreCivico;
    }

    public String getDebitoreCap() {
        return debitoreCap;
    }

    public void setDebitoreCap(String debitoreCap) {
        this.debitoreCap = debitoreCap;
    }

    public String getDebitoreLocalita() {
        return debitoreLocalita;
    }

    public void setDebitoreLocalita(String debitoreLocalita) {
        this.debitoreLocalita = debitoreLocalita;
    }

    public String getDebitoreProvincia() {
        return debitoreProvincia;
    }

    public void setDebitoreProvincia(String debitoreProvincia) {
        this.debitoreProvincia = debitoreProvincia;
    }

    public String getDebitoreNazione() {
        return debitoreNazione;
    }

    public void setDebitoreNazione(String debitoreNazione) {
        this.debitoreNazione = debitoreNazione;
    }

    public String getDebitoreEmail() {
        return debitoreEmail;
    }

    public void setDebitoreEmail(String debitoreEmail) {
        this.debitoreEmail = debitoreEmail;
    }

    public String getDebitoreCellulare() {
        return debitoreCellulare;
    }

    public void setDebitoreCellulare(String debitoreCellulare) {
        this.debitoreCellulare = debitoreCellulare;
    }

    public String getTassonomia() {
        return tassonomia;
    }

    public void setTassonomia(String tassonomia) {
        this.tassonomia = tassonomia;
    }

    public String getTassonomiaAvviso() {
        return tassonomiaAvviso;
    }

    public void setTassonomiaAvviso(String tassonomiaAvviso) {
        this.tassonomiaAvviso = tassonomiaAvviso;
    }

    public String getDatiAllegati() {
        return datiAllegati;
    }

    public void setDatiAllegati(String datiAllegati) {
        this.datiAllegati = datiAllegati;
    }

    public String getIuvVersamento() {
        return iuvVersamento;
    }

    public void setIuvVersamento(String iuvVersamento) {
        this.iuvVersamento = iuvVersamento;
    }

    public String getNumeroAvviso() {
        return numeroAvviso;
    }

    public void setNumeroAvviso(String numeroAvviso) {
        this.numeroAvviso = numeroAvviso;
    }

    public Boolean getAnomalo() {
        return anomalo;
    }

    public void setAnomalo(Boolean anomalo) {
        this.anomalo = anomalo;
    }

    public Boolean getAck() {
        return ack;
    }

    public void setAck(Boolean ack) {
        this.ack = ack;
    }

    public String getDivisione() {
        return divisione;
    }

    public void setDivisione(String divisione) {
        this.divisione = divisione;
    }

    public String getDirezione() {
        return direzione;
    }

    public void setDirezione(String direzione) {
        this.direzione = direzione;
    }

    public Double getImportoPagato() {
        return importoPagato;
    }

    public void setImportoPagato(Double importoPagato) {
        this.importoPagato = importoPagato;
    }

    public String getSrcDebitoreIdentificativo() {
        return srcDebitoreIdentificativo;
    }

    public void setSrcDebitoreIdentificativo(String srcDebitoreIdentificativo) {
        this.srcDebitoreIdentificativo = srcDebitoreIdentificativo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getProprieta() {
        return proprieta;
    }

    public void setProprieta(String proprieta) {
        this.proprieta = proprieta;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }

    public UnitaOperativa getUnitaOperativa() {
        return unitaOperativa;
    }

    public void setUnitaOperativa(UnitaOperativa unitaOperativa) {
        this.unitaOperativa = unitaOperativa;
    }

    public Applicazione getApplicazione() {
        return applicazione;
    }

    public void setApplicazione(Applicazione applicazione) {
        this.applicazione = applicazione;
    }

    public TipoVersamento getTipoVersamento() {
        return tipoVersamento;
    }

    public void setTipoVersamento(TipoVersamento tipoVersamento) {
        this.tipoVersamento = tipoVersamento;
    }

    public TipoVersamentoDominio getTipoVersamentoDominio() {
        return tipoVersamentoDominio;
    }

    public void setTipoVersamentoDominio(TipoVersamentoDominio tipoVersamentoDominio) {
        this.tipoVersamentoDominio = tipoVersamentoDominio;
    }

    public Documento getDocumento() {
        return documento;
    }

    public void setDocumento(Documento documento) {
        this.documento = documento;
    }

    public String getCodRata() {
        return codRata;
    }

    public void setCodRata(String codRata) {
        this.codRata = codRata;
    }

    public List<SingoloVersamento> getSingoliVersamenti() {
        return singoliVersamenti;
    }

    public void setSingoliVersamenti(List<SingoloVersamento> singoliVersamenti) {
        this.singoliVersamenti = singoliVersamenti;
    }
}
