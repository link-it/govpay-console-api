package it.govpay.console.operazioni;

/**
 * Operazione non collegata a un job batch remoto (nessun {@code url}
 * configurato in {@link it.govpay.console.config.OperazioniProperties.OperazioneConfig}):
 * eseguita sincronamente in-process. Ogni bean che la implementa si registra
 * per l'id restituito da {@link #getId()}.
 */
public interface OperazioneLocaleHandler {

    String getId();

    String getNome();

    String getDescrizione();

    void eseguire();
}
