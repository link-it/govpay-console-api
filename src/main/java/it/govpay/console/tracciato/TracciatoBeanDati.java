package it.govpay.console.tracciato;

/**
 * Stato fine-grain e contatori di un tracciato, serializzati in JSON nella
 * colonna {@code tracciati.bean_dati}. Mappa (solo i campi usati da V2) il
 * bean V1 {@code it.govpay.core.beans.tracciati.TracciatoPendenza}
 * (jars/core-beans): stesso nome campo, stessa convenzione camelCase Jackson.
 */
public class TracciatoBeanDati {

    private long numAddTotali;
    private long numAddOk;
    private long numAddKo;

    private long numDelTotali;
    private long numDelOk;
    private long numDelKo;

    private String dataUltimoAggiornamento;
    private String stepElaborazione;
    private String descrizioneStepElaborazione;

    private long numStampeTotali;
    private long numStampeOk;
    private long numStampeKo;

    private boolean stampaAvvisi;

    public long getNumAddTotali() {
        return numAddTotali;
    }

    public void setNumAddTotali(long numAddTotali) {
        this.numAddTotali = numAddTotali;
    }

    public long getNumAddOk() {
        return numAddOk;
    }

    public void setNumAddOk(long numAddOk) {
        this.numAddOk = numAddOk;
    }

    public long getNumAddKo() {
        return numAddKo;
    }

    public void setNumAddKo(long numAddKo) {
        this.numAddKo = numAddKo;
    }

    public long getNumDelTotali() {
        return numDelTotali;
    }

    public void setNumDelTotali(long numDelTotali) {
        this.numDelTotali = numDelTotali;
    }

    public long getNumDelOk() {
        return numDelOk;
    }

    public void setNumDelOk(long numDelOk) {
        this.numDelOk = numDelOk;
    }

    public long getNumDelKo() {
        return numDelKo;
    }

    public void setNumDelKo(long numDelKo) {
        this.numDelKo = numDelKo;
    }

    public String getDataUltimoAggiornamento() {
        return dataUltimoAggiornamento;
    }

    public void setDataUltimoAggiornamento(String dataUltimoAggiornamento) {
        this.dataUltimoAggiornamento = dataUltimoAggiornamento;
    }

    public String getStepElaborazione() {
        return stepElaborazione;
    }

    public void setStepElaborazione(String stepElaborazione) {
        this.stepElaborazione = stepElaborazione;
    }

    public String getDescrizioneStepElaborazione() {
        return descrizioneStepElaborazione;
    }

    public void setDescrizioneStepElaborazione(String descrizioneStepElaborazione) {
        this.descrizioneStepElaborazione = descrizioneStepElaborazione;
    }

    public long getNumStampeTotali() {
        return numStampeTotali;
    }

    public void setNumStampeTotali(long numStampeTotali) {
        this.numStampeTotali = numStampeTotali;
    }

    public long getNumStampeOk() {
        return numStampeOk;
    }

    public void setNumStampeOk(long numStampeOk) {
        this.numStampeOk = numStampeOk;
    }

    public long getNumStampeKo() {
        return numStampeKo;
    }

    public void setNumStampeKo(long numStampeKo) {
        this.numStampeKo = numStampeKo;
    }

    public boolean isStampaAvvisi() {
        return stampaAvvisi;
    }

    public void setStampaAvvisi(boolean stampaAvvisi) {
        this.stampaAvvisi = stampaAvvisi;
    }
}
