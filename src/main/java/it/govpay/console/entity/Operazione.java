package it.govpay.console.entity;

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
 * Singola operazione (riga) di un {@link Tracciato} di caricamento pendenze.
 * Mappa la tabella V1 {@code operazioni}, condivisa anche con altre
 * tipologie di tracciato (qui filtrate implicitamente da {@code id_tracciato}).
 *
 * <p>{@code dati_richiesta}/{@code dati_risposta} sono mappati come
 * {@code byte[]} normali (BYTEA/BLOB JSON, stesso pattern di
 * {@code Tracciato.rawRichiesta}): servono solo nel dettaglio
 * ({@code GET .../operazioni/{numero}}), non nella lista.
 */
@Entity
@Table(name = "operazioni")
@SequenceGenerator(name = "seq_operazioni", sequenceName = "seq_operazioni", allocationSize = 1)
public class Operazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_operazioni")
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tracciato", nullable = false)
    private Tracciato tracciato;

    @Column(name = "tipo_operazione", nullable = false, length = 16)
    private String tipoOperazione;

    @Column(name = "linea_elaborazione", nullable = false)
    private Long lineaElaborazione;

    @Column(name = "stato", nullable = false, length = 16)
    private String stato;

    @Column(name = "dettaglio_esito", length = 255)
    private String dettaglioEsito;

    @Column(name = "cod_versamento_ente", length = 255)
    private String codVersamentoEnte;

    @Column(name = "cod_dominio", length = 35)
    private String codDominio;

    @Column(name = "iuv", length = 35)
    private String iuv;

    @Column(name = "trn", length = 35)
    private String trn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_applicazione")
    private Applicazione applicazione;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_versamento")
    private Versamento versamento;

    @Column(name = "dati_richiesta", columnDefinition = "BYTEA")
    private byte[] datiRichiesta;

    @Column(name = "dati_risposta", columnDefinition = "BYTEA")
    private byte[] datiRisposta;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Tracciato getTracciato() {
        return tracciato;
    }

    public void setTracciato(Tracciato tracciato) {
        this.tracciato = tracciato;
    }

    public String getTipoOperazione() {
        return tipoOperazione;
    }

    public void setTipoOperazione(String tipoOperazione) {
        this.tipoOperazione = tipoOperazione;
    }

    public Long getLineaElaborazione() {
        return lineaElaborazione;
    }

    public void setLineaElaborazione(Long lineaElaborazione) {
        this.lineaElaborazione = lineaElaborazione;
    }

    public String getStato() {
        return stato;
    }

    public void setStato(String stato) {
        this.stato = stato;
    }

    public String getDettaglioEsito() {
        return dettaglioEsito;
    }

    public void setDettaglioEsito(String dettaglioEsito) {
        this.dettaglioEsito = dettaglioEsito;
    }

    public String getCodVersamentoEnte() {
        return codVersamentoEnte;
    }

    public void setCodVersamentoEnte(String codVersamentoEnte) {
        this.codVersamentoEnte = codVersamentoEnte;
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

    public String getTrn() {
        return trn;
    }

    public void setTrn(String trn) {
        this.trn = trn;
    }

    public Applicazione getApplicazione() {
        return applicazione;
    }

    public void setApplicazione(Applicazione applicazione) {
        this.applicazione = applicazione;
    }

    public Versamento getVersamento() {
        return versamento;
    }

    public void setVersamento(Versamento versamento) {
        this.versamento = versamento;
    }

    public byte[] getDatiRichiesta() {
        return datiRichiesta;
    }

    public void setDatiRichiesta(byte[] datiRichiesta) {
        this.datiRichiesta = datiRichiesta;
    }

    public byte[] getDatiRisposta() {
        return datiRisposta;
    }

    public void setDatiRisposta(byte[] datiRisposta) {
        this.datiRisposta = datiRisposta;
    }
}
