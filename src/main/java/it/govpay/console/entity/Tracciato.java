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
 * Tracciato di caricamento massivo pendenze. Mappa la tabella V1 {@code tracciati}
 * (condivisa con altre tipologie di tracciato, qui filtrate per {@code tipo=PENDENZA}).
 *
 * <p>{@code raw_richiesta} e' mappato come normale campo {@code byte[]}
 * (stesso pattern di {@code Dominio.logo}): e' BYTEA/BLOB portabile su tutti
 * i dialetti, nessuna incoerenza di tipo tra DB. {@code raw_esito} e
 * {@code zip_stampe} restano invece fuori da questa entity (fuori scope di
 * questo commit — solo sub-resource): {@code zip_stampe} in particolare e'
 * tipizzato {@code OID} su Postgres ma {@code BLOB} sugli altri DB, non
 * mappabile in modo uniforme con JPA (letto via JDBC diretto quando servira').
 *
 * <p>{@code cod_dominio} e' l'unica colonna di legame al dominio (la tabella
 * non ha un {@code id_dominio} numerico come {@code fr}): la relazione verso
 * {@link Dominio} e' quindi sulla colonna naturale univoca {@code cod_dominio}
 * (vincolo {@code unique_domini_1}), non su una FK tecnica.
 */
@Entity
@Table(name = "tracciati")
@SequenceGenerator(name = "seq_tracciati", sequenceName = "seq_tracciati", allocationSize = 1)
public class Tracciato {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tracciati")
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cod_dominio", referencedColumnName = "cod_dominio", nullable = false)
    private Dominio dominio;

    @Column(name = "cod_tipo_versamento", length = 35)
    private String codTipoVersamento;

    @Column(name = "formato", nullable = false, length = 10)
    private String formato;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    @Column(name = "stato", nullable = false, length = 12)
    private String stato;

    @Column(name = "descrizione_stato", length = 256)
    private String descrizioneStato;

    @Column(name = "data_caricamento", nullable = false)
    private OffsetDateTime dataCaricamento;

    @Column(name = "data_completamento")
    private OffsetDateTime dataCompletamento;

    /** JSON di {@code it.govpay.core.beans.tracciati.TracciatoPendenza} (V1): stato fine-grain e contatori. */
    @Column(name = "bean_dati", columnDefinition = "TEXT")
    private String beanDati;

    @Column(name = "file_name_richiesta", length = 256)
    private String fileNameRichiesta;

    @Column(name = "file_name_esito", length = 256)
    private String fileNameEsito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operatore")
    private Operatore operatore;

    @Column(name = "raw_richiesta", columnDefinition = "BYTEA")
    private byte[] rawRichiesta;

    @Column(name = "raw_esito", columnDefinition = "BYTEA")
    private byte[] rawEsito;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }

    public String getCodTipoVersamento() {
        return codTipoVersamento;
    }

    public void setCodTipoVersamento(String codTipoVersamento) {
        this.codTipoVersamento = codTipoVersamento;
    }

    public String getFormato() {
        return formato;
    }

    public void setFormato(String formato) {
        this.formato = formato;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
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

    public OffsetDateTime getDataCaricamento() {
        return dataCaricamento;
    }

    public void setDataCaricamento(OffsetDateTime dataCaricamento) {
        this.dataCaricamento = dataCaricamento;
    }

    public OffsetDateTime getDataCompletamento() {
        return dataCompletamento;
    }

    public void setDataCompletamento(OffsetDateTime dataCompletamento) {
        this.dataCompletamento = dataCompletamento;
    }

    public String getBeanDati() {
        return beanDati;
    }

    public void setBeanDati(String beanDati) {
        this.beanDati = beanDati;
    }

    public String getFileNameRichiesta() {
        return fileNameRichiesta;
    }

    public void setFileNameRichiesta(String fileNameRichiesta) {
        this.fileNameRichiesta = fileNameRichiesta;
    }

    public String getFileNameEsito() {
        return fileNameEsito;
    }

    public void setFileNameEsito(String fileNameEsito) {
        this.fileNameEsito = fileNameEsito;
    }

    public Operatore getOperatore() {
        return operatore;
    }

    public void setOperatore(Operatore operatore) {
        this.operatore = operatore;
    }

    public byte[] getRawRichiesta() {
        return rawRichiesta;
    }

    public void setRawRichiesta(byte[] rawRichiesta) {
        this.rawRichiesta = rawRichiesta;
    }

    public byte[] getRawEsito() {
        return rawEsito;
    }

    public void setRawEsito(byte[] rawEsito) {
        this.rawEsito = rawEsito;
    }
}
