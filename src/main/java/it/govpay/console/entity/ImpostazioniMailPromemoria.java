package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Template FreeMarker di un promemoria spedito via mail (una riga per tipo,
 * chiave naturale {@code tipo_promemoria}: {@code AVVISO}/{@code RICEVUTA}/
 * {@code SCADENZA}, set fisso mai esteso a runtime). Le colonne
 * {@code allega_pdf}/{@code solo_eseguiti}/{@code preavviso} sono significative
 * solo per i tipi a cui si applicano (rispettivamente avviso+ricevuta,
 * ricevuta, scadenza).
 */
@Entity
@Table(name = "impostazioni_mail_promemoria")
public class ImpostazioniMailPromemoria {

    public static final String AVVISO = "AVVISO";
    public static final String RICEVUTA = "RICEVUTA";
    public static final String SCADENZA = "SCADENZA";

    @Id
    @Column(name = "tipo_promemoria", length = 20)
    private String tipoPromemoria;

    @Column(name = "oggetto", length = 4000)
    private String oggetto;

    @Column(name = "messaggio", length = 4000)
    private String messaggio;

    @Column(name = "allega_pdf")
    private Boolean allegaPdf;

    @Column(name = "solo_eseguiti")
    private Boolean soloEseguiti;

    @Column(name = "preavviso")
    private Integer preavviso;

    public String getTipoPromemoria() {
        return tipoPromemoria;
    }

    public void setTipoPromemoria(String tipoPromemoria) {
        this.tipoPromemoria = tipoPromemoria;
    }

    public String getOggetto() {
        return oggetto;
    }

    public void setOggetto(String oggetto) {
        this.oggetto = oggetto;
    }

    public String getMessaggio() {
        return messaggio;
    }

    public void setMessaggio(String messaggio) {
        this.messaggio = messaggio;
    }

    public Boolean getAllegaPdf() {
        return allegaPdf;
    }

    public void setAllegaPdf(Boolean allegaPdf) {
        this.allegaPdf = allegaPdf;
    }

    public Boolean getSoloEseguiti() {
        return soloEseguiti;
    }

    public void setSoloEseguiti(Boolean soloEseguiti) {
        this.soloEseguiti = soloEseguiti;
    }

    public Integer getPreavviso() {
        return preavviso;
    }

    public void setPreavviso(Integer preavviso) {
        this.preavviso = preavviso;
    }
}
