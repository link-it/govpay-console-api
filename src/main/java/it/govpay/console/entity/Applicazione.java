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
@Table(name = "applicazioni")
@SequenceGenerator(name = "seq_applicazioni", sequenceName = "seq_applicazioni", allocationSize = 1)
public class Applicazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_applicazioni")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_applicazione", nullable = false, length = 35)
    private String codApplicazione;

    // Nello schema reale id_utenza e' NOT NULL: ogni applicazione ha la sua
    // utenza tecnica. La colonna non e' marcata nullable=false qui per non
    // vincolare lo schema generato in test (dove alcune fixture di altre suite
    // creano applicazioni-segnaposto senza utenza); il servizio la valorizza
    // sempre in scrittura.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_utenza")
    private Utenza utenza;

    @Column(name = "trusted", nullable = false)
    private Boolean trusted = false;

    @Column(name = "auto_iuv", nullable = false)
    private Boolean autoIuv = false;

    @Column(name = "firma_ricevuta", nullable = false, length = 1)
    private String firmaRicevuta = "0";

    @Column(name = "cod_applicazione_iuv", length = 3)
    private String codApplicazioneIuv;

    @Column(name = "reg_exp", length = 1024)
    private String regExp;

    @Column(name = "cod_connettore_integrazione", length = 255)
    private String codConnettoreIntegrazione;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodApplicazione() {
        return codApplicazione;
    }

    public void setCodApplicazione(String codApplicazione) {
        this.codApplicazione = codApplicazione;
    }

    public Utenza getUtenza() {
        return utenza;
    }

    public void setUtenza(Utenza utenza) {
        this.utenza = utenza;
    }

    public Boolean getTrusted() {
        return trusted;
    }

    public void setTrusted(Boolean trusted) {
        this.trusted = trusted;
    }

    public Boolean getAutoIuv() {
        return autoIuv;
    }

    public void setAutoIuv(Boolean autoIuv) {
        this.autoIuv = autoIuv;
    }

    public String getFirmaRicevuta() {
        return firmaRicevuta;
    }

    public void setFirmaRicevuta(String firmaRicevuta) {
        this.firmaRicevuta = firmaRicevuta;
    }

    public String getCodApplicazioneIuv() {
        return codApplicazioneIuv;
    }

    public void setCodApplicazioneIuv(String codApplicazioneIuv) {
        this.codApplicazioneIuv = codApplicazioneIuv;
    }

    public String getRegExp() {
        return regExp;
    }

    public void setRegExp(String regExp) {
        this.regExp = regExp;
    }

    public String getCodConnettoreIntegrazione() {
        return codConnettoreIntegrazione;
    }

    public void setCodConnettoreIntegrazione(String codConnettoreIntegrazione) {
        this.codConnettoreIntegrazione = codConnettoreIntegrazione;
    }
}
