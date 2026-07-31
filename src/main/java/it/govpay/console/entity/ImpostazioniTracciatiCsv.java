package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Template FreeMarker per la generazione dei tracciati CSV di risposta. Riga
 * singola, chiave fissa {@link #ID_SINGLETON}.
 */
@Entity
@Table(name = "impostazioni_tracciati_csv")
public class ImpostazioniTracciatiCsv {

    public static final Integer ID_SINGLETON = 1;

    @Id
    @Column(name = "id")
    private Integer id = ID_SINGLETON;

    @Column(name = "tipo", length = 20)
    private String tipo;

    @Column(name = "intestazione", length = 4000)
    private String intestazione;

    @Column(name = "richiesta", length = 4000)
    private String richiesta;

    @Column(name = "risposta", length = 4000)
    private String risposta;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getIntestazione() {
        return intestazione;
    }

    public void setIntestazione(String intestazione) {
        this.intestazione = intestazione;
    }

    public String getRichiesta() {
        return richiesta;
    }

    public void setRichiesta(String richiesta) {
        this.richiesta = richiesta;
    }

    public String getRisposta() {
        return risposta;
    }

    public void setRisposta(String risposta) {
        this.risposta = risposta;
    }
}
