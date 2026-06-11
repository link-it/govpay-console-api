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

@Entity
@Table(name = "singoli_versamenti")
@SequenceGenerator(name = "seq_singoli_versamenti", sequenceName = "seq_singoli_versamenti", allocationSize = 1)
public class SingoloVersamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_singoli_versamenti")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_singolo_versamento_ente", nullable = false, length = 70)
    private String codSingoloVersamentoEnte;

    @Column(name = "stato_singolo_versamento", nullable = false, length = 35)
    private String statoSingoloVersamento;

    @Column(name = "importo_singolo_versamento", nullable = false)
    private Double importoSingoloVersamento;

    @Column(name = "descrizione", length = 256)
    private String descrizione;

    @Column(name = "descrizione_causale_rpt", length = 140)
    private String descrizioneCausaleRpt;

    @Column(name = "dati_allegati", columnDefinition = "TEXT")
    private String datiAllegati;

    @Column(name = "indice_dati", nullable = false)
    private Integer indiceDati;

    @Column(name = "contabilita", columnDefinition = "TEXT")
    private String contabilita;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_versamento", nullable = false)
    private Versamento versamento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodSingoloVersamentoEnte() {
        return codSingoloVersamentoEnte;
    }

    public void setCodSingoloVersamentoEnte(String codSingoloVersamentoEnte) {
        this.codSingoloVersamentoEnte = codSingoloVersamentoEnte;
    }

    public String getStatoSingoloVersamento() {
        return statoSingoloVersamento;
    }

    public void setStatoSingoloVersamento(String statoSingoloVersamento) {
        this.statoSingoloVersamento = statoSingoloVersamento;
    }

    public Double getImportoSingoloVersamento() {
        return importoSingoloVersamento;
    }

    public void setImportoSingoloVersamento(Double importoSingoloVersamento) {
        this.importoSingoloVersamento = importoSingoloVersamento;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getDescrizioneCausaleRpt() {
        return descrizioneCausaleRpt;
    }

    public void setDescrizioneCausaleRpt(String descrizioneCausaleRpt) {
        this.descrizioneCausaleRpt = descrizioneCausaleRpt;
    }

    public String getDatiAllegati() {
        return datiAllegati;
    }

    public void setDatiAllegati(String datiAllegati) {
        this.datiAllegati = datiAllegati;
    }

    public Integer getIndiceDati() {
        return indiceDati;
    }

    public void setIndiceDati(Integer indiceDati) {
        this.indiceDati = indiceDati;
    }

    public String getContabilita() {
        return contabilita;
    }

    public void setContabilita(String contabilita) {
        this.contabilita = contabilita;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Versamento getVersamento() {
        return versamento;
    }

    public void setVersamento(Versamento versamento) {
        this.versamento = versamento;
    }
}
