package it.govpay.console.avviso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lettura parziale di {@code Versamento.proprieta} (colonna JSON, stesso formato del legacy
 * {@code it.govpay.core.beans.tracciati.ProprietaPendenza}): modella solo i campi usati per
 * l'avviso PDF, {@code ignoreUnknown} perche' il blob reale ne contiene molti altri
 * (descrizioneImporto, lineaTestoRicevuta1/2, dataScandenzaAvviso, ecc.) non ancora rilevanti
 * qui.
 *
 * <p>{@code linguaSecondariaInformativaImportoAvviso} e' letto dal chiamante solo se
 * {@code informativaImportoAvviso} e' valorizzato — stesso vincolo imposto lato
 * govpay-stampe-api, non replicato qui perche' questa classe e' solo lettura del JSON.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProprietaPendenza {

    private String informativaImportoAvviso;
    private String linguaSecondariaCausale;
    private String linguaSecondariaInformativaImportoAvviso;

    public String getInformativaImportoAvviso() {
        return informativaImportoAvviso;
    }

    public void setInformativaImportoAvviso(String informativaImportoAvviso) {
        this.informativaImportoAvviso = informativaImportoAvviso;
    }

    public String getLinguaSecondariaCausale() {
        return linguaSecondariaCausale;
    }

    public void setLinguaSecondariaCausale(String linguaSecondariaCausale) {
        this.linguaSecondariaCausale = linguaSecondariaCausale;
    }

    public String getLinguaSecondariaInformativaImportoAvviso() {
        return linguaSecondariaInformativaImportoAvviso;
    }

    public void setLinguaSecondariaInformativaImportoAvviso(String linguaSecondariaInformativaImportoAvviso) {
        this.linguaSecondariaInformativaImportoAvviso = linguaSecondariaInformativaImportoAvviso;
    }
}
