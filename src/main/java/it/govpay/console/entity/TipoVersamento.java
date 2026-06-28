package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "tipi_versamento")
@SequenceGenerator(name = "seq_tipi_versamento", sequenceName = "seq_tipi_versamento", allocationSize = 1)
public class TipoVersamento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tipi_versamento")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_tipo_versamento", nullable = false, length = 35)
    private String codTipoVersamento;

    @Column(name = "descrizione", nullable = false, length = 255)
    private String descrizione;

    @Column(name = "codifica_iuv", length = 4)
    private String codificaIuv;

    @Column(name = "paga_terzi")
    private Boolean pagaTerzi;

    @Column(name = "abilitato")
    private Boolean abilitato;

    @Column(name = "bo_form_tipo", length = 35)
    private String boFormTipo;

    @Column(name = "bo_form_definizione", columnDefinition = "TEXT")
    private String boFormDefinizione;

    @Column(name = "bo_validazione_def", columnDefinition = "TEXT")
    private String boValidazioneDef;

    @Column(name = "bo_trasformazione_tipo", length = 35)
    private String boTrasformazioneTipo;

    @Column(name = "bo_trasformazione_def", columnDefinition = "TEXT")
    private String boTrasformazioneDef;

    @Column(name = "bo_cod_applicazione", length = 35)
    private String boCodApplicazione;

    @Column(name = "bo_abilitato")
    private Boolean boAbilitato;

    @Column(name = "pag_form_tipo", length = 35)
    private String pagFormTipo;

    @Column(name = "pag_form_definizione", columnDefinition = "TEXT")
    private String pagFormDefinizione;

    @Column(name = "pag_form_impaginazione", columnDefinition = "TEXT")
    private String pagFormImpaginazione;

    @Column(name = "pag_validazione_def", columnDefinition = "TEXT")
    private String pagValidazioneDef;

    @Column(name = "pag_trasformazione_tipo", length = 35)
    private String pagTrasformazioneTipo;

    @Column(name = "pag_trasformazione_def", columnDefinition = "TEXT")
    private String pagTrasformazioneDef;

    @Column(name = "pag_cod_applicazione", length = 35)
    private String pagCodApplicazione;

    @Column(name = "pag_abilitato")
    private Boolean pagAbilitato;

    @Column(name = "avv_mail_prom_avv_abilitato")
    private Boolean avvMailPromAvvAbilitato;

    @Column(name = "avv_mail_prom_avv_pdf")
    private Boolean avvMailPromAvvPdf;

    @Column(name = "avv_mail_prom_avv_tipo", length = 35)
    private String avvMailPromAvvTipo;

    @Column(name = "avv_mail_prom_avv_oggetto", columnDefinition = "TEXT")
    private String avvMailPromAvvOggetto;

    @Column(name = "avv_mail_prom_avv_messaggio", columnDefinition = "TEXT")
    private String avvMailPromAvvMessaggio;

    @Column(name = "avv_mail_prom_ric_abilitato")
    private Boolean avvMailPromRicAbilitato;

    @Column(name = "avv_mail_prom_ric_pdf")
    private Boolean avvMailPromRicPdf;

    @Column(name = "avv_mail_prom_ric_tipo", length = 35)
    private String avvMailPromRicTipo;

    @Column(name = "avv_mail_prom_ric_oggetto", columnDefinition = "TEXT")
    private String avvMailPromRicOggetto;

    @Column(name = "avv_mail_prom_ric_messaggio", columnDefinition = "TEXT")
    private String avvMailPromRicMessaggio;

    @Column(name = "avv_mail_prom_ric_eseguiti")
    private Boolean avvMailPromRicEseguiti;

    @Column(name = "avv_mail_prom_scad_abilitato")
    private Boolean avvMailPromScadAbilitato;

    @Column(name = "avv_mail_prom_scad_preavviso")
    private Integer avvMailPromScadPreavviso;

    @Column(name = "avv_mail_prom_scad_tipo", length = 35)
    private String avvMailPromScadTipo;

    @Column(name = "avv_mail_prom_scad_oggetto", columnDefinition = "TEXT")
    private String avvMailPromScadOggetto;

    @Column(name = "avv_mail_prom_scad_messaggio", columnDefinition = "TEXT")
    private String avvMailPromScadMessaggio;

    @Column(name = "visualizzazione_definizione", columnDefinition = "TEXT")
    private String visualizzazioneDefinizione;

    @Column(name = "trac_csv_tipo", length = 35)
    private String tracCsvTipo;

    @Column(name = "trac_csv_header_risposta", columnDefinition = "TEXT")
    private String tracCsvHeaderRisposta;

    @Column(name = "trac_csv_template_richiesta", columnDefinition = "TEXT")
    private String tracCsvTemplateRichiesta;

    @Column(name = "trac_csv_template_risposta", columnDefinition = "TEXT")
    private String tracCsvTemplateRisposta;

    @Column(name = "avv_app_io_prom_avv_abilitato")
    private Boolean avvAppIoPromAvvAbilitato;

    @Column(name = "avv_app_io_prom_avv_tipo", length = 35)
    private String avvAppIoPromAvvTipo;

    @Column(name = "avv_app_io_prom_avv_oggetto", columnDefinition = "TEXT")
    private String avvAppIoPromAvvOggetto;

    @Column(name = "avv_app_io_prom_avv_messaggio", columnDefinition = "TEXT")
    private String avvAppIoPromAvvMessaggio;

    @Column(name = "avv_app_io_prom_ric_abilitato")
    private Boolean avvAppIoPromRicAbilitato;

    @Column(name = "avv_app_io_prom_ric_tipo", length = 35)
    private String avvAppIoPromRicTipo;

    @Column(name = "avv_app_io_prom_ric_oggetto", columnDefinition = "TEXT")
    private String avvAppIoPromRicOggetto;

    @Column(name = "avv_app_io_prom_ric_messaggio", columnDefinition = "TEXT")
    private String avvAppIoPromRicMessaggio;

    @Column(name = "avv_app_io_prom_ric_eseguiti")
    private Boolean avvAppIoPromRicEseguiti;

    @Column(name = "avv_app_io_prom_scad_abilitato")
    private Boolean avvAppIoPromScadAbilitato;

    @Column(name = "avv_app_io_prom_scad_preavviso")
    private Integer avvAppIoPromScadPreavviso;

    @Column(name = "avv_app_io_prom_scad_tipo", length = 35)
    private String avvAppIoPromScadTipo;

    @Column(name = "avv_app_io_prom_scad_oggetto", columnDefinition = "TEXT")
    private String avvAppIoPromScadOggetto;

    @Column(name = "avv_app_io_prom_scad_messaggio", columnDefinition = "TEXT")
    private String avvAppIoPromScadMessaggio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodTipoVersamento() {
        return codTipoVersamento;
    }

    public void setCodTipoVersamento(String codTipoVersamento) {
        this.codTipoVersamento = codTipoVersamento;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getCodificaIuv() {
        return codificaIuv;
    }

    public void setCodificaIuv(String codificaIuv) {
        this.codificaIuv = codificaIuv;
    }

    public Boolean getPagaTerzi() {
        return pagaTerzi;
    }

    public void setPagaTerzi(Boolean pagaTerzi) {
        this.pagaTerzi = pagaTerzi;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }

    public String getBoFormTipo() {
        return boFormTipo;
    }

    public void setBoFormTipo(String boFormTipo) {
        this.boFormTipo = boFormTipo;
    }

    public String getBoFormDefinizione() {
        return boFormDefinizione;
    }

    public void setBoFormDefinizione(String boFormDefinizione) {
        this.boFormDefinizione = boFormDefinizione;
    }

    public String getBoValidazioneDef() {
        return boValidazioneDef;
    }

    public void setBoValidazioneDef(String boValidazioneDef) {
        this.boValidazioneDef = boValidazioneDef;
    }

    public String getBoTrasformazioneTipo() {
        return boTrasformazioneTipo;
    }

    public void setBoTrasformazioneTipo(String boTrasformazioneTipo) {
        this.boTrasformazioneTipo = boTrasformazioneTipo;
    }

    public String getBoTrasformazioneDef() {
        return boTrasformazioneDef;
    }

    public void setBoTrasformazioneDef(String boTrasformazioneDef) {
        this.boTrasformazioneDef = boTrasformazioneDef;
    }

    public String getBoCodApplicazione() {
        return boCodApplicazione;
    }

    public void setBoCodApplicazione(String boCodApplicazione) {
        this.boCodApplicazione = boCodApplicazione;
    }

    public Boolean getBoAbilitato() {
        return boAbilitato;
    }

    public void setBoAbilitato(Boolean boAbilitato) {
        this.boAbilitato = boAbilitato;
    }

    public String getPagFormTipo() {
        return pagFormTipo;
    }

    public void setPagFormTipo(String pagFormTipo) {
        this.pagFormTipo = pagFormTipo;
    }

    public String getPagFormDefinizione() {
        return pagFormDefinizione;
    }

    public void setPagFormDefinizione(String pagFormDefinizione) {
        this.pagFormDefinizione = pagFormDefinizione;
    }

    public String getPagFormImpaginazione() {
        return pagFormImpaginazione;
    }

    public void setPagFormImpaginazione(String pagFormImpaginazione) {
        this.pagFormImpaginazione = pagFormImpaginazione;
    }

    public String getPagValidazioneDef() {
        return pagValidazioneDef;
    }

    public void setPagValidazioneDef(String pagValidazioneDef) {
        this.pagValidazioneDef = pagValidazioneDef;
    }

    public String getPagTrasformazioneTipo() {
        return pagTrasformazioneTipo;
    }

    public void setPagTrasformazioneTipo(String pagTrasformazioneTipo) {
        this.pagTrasformazioneTipo = pagTrasformazioneTipo;
    }

    public String getPagTrasformazioneDef() {
        return pagTrasformazioneDef;
    }

    public void setPagTrasformazioneDef(String pagTrasformazioneDef) {
        this.pagTrasformazioneDef = pagTrasformazioneDef;
    }

    public String getPagCodApplicazione() {
        return pagCodApplicazione;
    }

    public void setPagCodApplicazione(String pagCodApplicazione) {
        this.pagCodApplicazione = pagCodApplicazione;
    }

    public Boolean getPagAbilitato() {
        return pagAbilitato;
    }

    public void setPagAbilitato(Boolean pagAbilitato) {
        this.pagAbilitato = pagAbilitato;
    }

    public Boolean getAvvMailPromAvvAbilitato() {
        return avvMailPromAvvAbilitato;
    }

    public void setAvvMailPromAvvAbilitato(Boolean avvMailPromAvvAbilitato) {
        this.avvMailPromAvvAbilitato = avvMailPromAvvAbilitato;
    }

    public Boolean getAvvMailPromAvvPdf() {
        return avvMailPromAvvPdf;
    }

    public void setAvvMailPromAvvPdf(Boolean avvMailPromAvvPdf) {
        this.avvMailPromAvvPdf = avvMailPromAvvPdf;
    }

    public String getAvvMailPromAvvTipo() {
        return avvMailPromAvvTipo;
    }

    public void setAvvMailPromAvvTipo(String avvMailPromAvvTipo) {
        this.avvMailPromAvvTipo = avvMailPromAvvTipo;
    }

    public String getAvvMailPromAvvOggetto() {
        return avvMailPromAvvOggetto;
    }

    public void setAvvMailPromAvvOggetto(String avvMailPromAvvOggetto) {
        this.avvMailPromAvvOggetto = avvMailPromAvvOggetto;
    }

    public String getAvvMailPromAvvMessaggio() {
        return avvMailPromAvvMessaggio;
    }

    public void setAvvMailPromAvvMessaggio(String avvMailPromAvvMessaggio) {
        this.avvMailPromAvvMessaggio = avvMailPromAvvMessaggio;
    }

    public Boolean getAvvMailPromRicAbilitato() {
        return avvMailPromRicAbilitato;
    }

    public void setAvvMailPromRicAbilitato(Boolean avvMailPromRicAbilitato) {
        this.avvMailPromRicAbilitato = avvMailPromRicAbilitato;
    }

    public Boolean getAvvMailPromRicPdf() {
        return avvMailPromRicPdf;
    }

    public void setAvvMailPromRicPdf(Boolean avvMailPromRicPdf) {
        this.avvMailPromRicPdf = avvMailPromRicPdf;
    }

    public String getAvvMailPromRicTipo() {
        return avvMailPromRicTipo;
    }

    public void setAvvMailPromRicTipo(String avvMailPromRicTipo) {
        this.avvMailPromRicTipo = avvMailPromRicTipo;
    }

    public String getAvvMailPromRicOggetto() {
        return avvMailPromRicOggetto;
    }

    public void setAvvMailPromRicOggetto(String avvMailPromRicOggetto) {
        this.avvMailPromRicOggetto = avvMailPromRicOggetto;
    }

    public String getAvvMailPromRicMessaggio() {
        return avvMailPromRicMessaggio;
    }

    public void setAvvMailPromRicMessaggio(String avvMailPromRicMessaggio) {
        this.avvMailPromRicMessaggio = avvMailPromRicMessaggio;
    }

    public Boolean getAvvMailPromRicEseguiti() {
        return avvMailPromRicEseguiti;
    }

    public void setAvvMailPromRicEseguiti(Boolean avvMailPromRicEseguiti) {
        this.avvMailPromRicEseguiti = avvMailPromRicEseguiti;
    }

    public Boolean getAvvMailPromScadAbilitato() {
        return avvMailPromScadAbilitato;
    }

    public void setAvvMailPromScadAbilitato(Boolean avvMailPromScadAbilitato) {
        this.avvMailPromScadAbilitato = avvMailPromScadAbilitato;
    }

    public Integer getAvvMailPromScadPreavviso() {
        return avvMailPromScadPreavviso;
    }

    public void setAvvMailPromScadPreavviso(Integer avvMailPromScadPreavviso) {
        this.avvMailPromScadPreavviso = avvMailPromScadPreavviso;
    }

    public String getAvvMailPromScadTipo() {
        return avvMailPromScadTipo;
    }

    public void setAvvMailPromScadTipo(String avvMailPromScadTipo) {
        this.avvMailPromScadTipo = avvMailPromScadTipo;
    }

    public String getAvvMailPromScadOggetto() {
        return avvMailPromScadOggetto;
    }

    public void setAvvMailPromScadOggetto(String avvMailPromScadOggetto) {
        this.avvMailPromScadOggetto = avvMailPromScadOggetto;
    }

    public String getAvvMailPromScadMessaggio() {
        return avvMailPromScadMessaggio;
    }

    public void setAvvMailPromScadMessaggio(String avvMailPromScadMessaggio) {
        this.avvMailPromScadMessaggio = avvMailPromScadMessaggio;
    }

    public String getVisualizzazioneDefinizione() {
        return visualizzazioneDefinizione;
    }

    public void setVisualizzazioneDefinizione(String visualizzazioneDefinizione) {
        this.visualizzazioneDefinizione = visualizzazioneDefinizione;
    }

    public String getTracCsvTipo() {
        return tracCsvTipo;
    }

    public void setTracCsvTipo(String tracCsvTipo) {
        this.tracCsvTipo = tracCsvTipo;
    }

    public String getTracCsvHeaderRisposta() {
        return tracCsvHeaderRisposta;
    }

    public void setTracCsvHeaderRisposta(String tracCsvHeaderRisposta) {
        this.tracCsvHeaderRisposta = tracCsvHeaderRisposta;
    }

    public String getTracCsvTemplateRichiesta() {
        return tracCsvTemplateRichiesta;
    }

    public void setTracCsvTemplateRichiesta(String tracCsvTemplateRichiesta) {
        this.tracCsvTemplateRichiesta = tracCsvTemplateRichiesta;
    }

    public String getTracCsvTemplateRisposta() {
        return tracCsvTemplateRisposta;
    }

    public void setTracCsvTemplateRisposta(String tracCsvTemplateRisposta) {
        this.tracCsvTemplateRisposta = tracCsvTemplateRisposta;
    }

    public Boolean getAvvAppIoPromAvvAbilitato() {
        return avvAppIoPromAvvAbilitato;
    }

    public void setAvvAppIoPromAvvAbilitato(Boolean avvAppIoPromAvvAbilitato) {
        this.avvAppIoPromAvvAbilitato = avvAppIoPromAvvAbilitato;
    }

    public String getAvvAppIoPromAvvTipo() {
        return avvAppIoPromAvvTipo;
    }

    public void setAvvAppIoPromAvvTipo(String avvAppIoPromAvvTipo) {
        this.avvAppIoPromAvvTipo = avvAppIoPromAvvTipo;
    }

    public String getAvvAppIoPromAvvOggetto() {
        return avvAppIoPromAvvOggetto;
    }

    public void setAvvAppIoPromAvvOggetto(String avvAppIoPromAvvOggetto) {
        this.avvAppIoPromAvvOggetto = avvAppIoPromAvvOggetto;
    }

    public String getAvvAppIoPromAvvMessaggio() {
        return avvAppIoPromAvvMessaggio;
    }

    public void setAvvAppIoPromAvvMessaggio(String avvAppIoPromAvvMessaggio) {
        this.avvAppIoPromAvvMessaggio = avvAppIoPromAvvMessaggio;
    }

    public Boolean getAvvAppIoPromRicAbilitato() {
        return avvAppIoPromRicAbilitato;
    }

    public void setAvvAppIoPromRicAbilitato(Boolean avvAppIoPromRicAbilitato) {
        this.avvAppIoPromRicAbilitato = avvAppIoPromRicAbilitato;
    }

    public String getAvvAppIoPromRicTipo() {
        return avvAppIoPromRicTipo;
    }

    public void setAvvAppIoPromRicTipo(String avvAppIoPromRicTipo) {
        this.avvAppIoPromRicTipo = avvAppIoPromRicTipo;
    }

    public String getAvvAppIoPromRicOggetto() {
        return avvAppIoPromRicOggetto;
    }

    public void setAvvAppIoPromRicOggetto(String avvAppIoPromRicOggetto) {
        this.avvAppIoPromRicOggetto = avvAppIoPromRicOggetto;
    }

    public String getAvvAppIoPromRicMessaggio() {
        return avvAppIoPromRicMessaggio;
    }

    public void setAvvAppIoPromRicMessaggio(String avvAppIoPromRicMessaggio) {
        this.avvAppIoPromRicMessaggio = avvAppIoPromRicMessaggio;
    }

    public Boolean getAvvAppIoPromRicEseguiti() {
        return avvAppIoPromRicEseguiti;
    }

    public void setAvvAppIoPromRicEseguiti(Boolean avvAppIoPromRicEseguiti) {
        this.avvAppIoPromRicEseguiti = avvAppIoPromRicEseguiti;
    }

    public Boolean getAvvAppIoPromScadAbilitato() {
        return avvAppIoPromScadAbilitato;
    }

    public void setAvvAppIoPromScadAbilitato(Boolean avvAppIoPromScadAbilitato) {
        this.avvAppIoPromScadAbilitato = avvAppIoPromScadAbilitato;
    }

    public Integer getAvvAppIoPromScadPreavviso() {
        return avvAppIoPromScadPreavviso;
    }

    public void setAvvAppIoPromScadPreavviso(Integer avvAppIoPromScadPreavviso) {
        this.avvAppIoPromScadPreavviso = avvAppIoPromScadPreavviso;
    }

    public String getAvvAppIoPromScadTipo() {
        return avvAppIoPromScadTipo;
    }

    public void setAvvAppIoPromScadTipo(String avvAppIoPromScadTipo) {
        this.avvAppIoPromScadTipo = avvAppIoPromScadTipo;
    }

    public String getAvvAppIoPromScadOggetto() {
        return avvAppIoPromScadOggetto;
    }

    public void setAvvAppIoPromScadOggetto(String avvAppIoPromScadOggetto) {
        this.avvAppIoPromScadOggetto = avvAppIoPromScadOggetto;
    }

    public String getAvvAppIoPromScadMessaggio() {
        return avvAppIoPromScadMessaggio;
    }

    public void setAvvAppIoPromScadMessaggio(String avvAppIoPromScadMessaggio) {
        this.avvAppIoPromScadMessaggio = avvAppIoPromScadMessaggio;
    }

}
