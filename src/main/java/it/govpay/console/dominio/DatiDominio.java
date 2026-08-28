package it.govpay.console.dominio;

/**
 * I campi scrivibili di un dominio, nella forma in cui arrivano dai DTO di
 * create/replace/patch. Raccolti in un record perche' alimentavano due elenchi di
 * parametri lunghi e in parte sovrapposti — tredici per la scrittura sull'entity e
 * otto per la validazione semantica — ripetuti identici su tre percorsi.
 */
public record DatiDominio(String ragioneSociale, String gln, String cbill, String iuvPrefix,
        String autStampaPoste, Integer auxDigit, Integer segregationCode, String tassonomia,
        Boolean intermediato, Boolean scaricaFr, Boolean abilitato, String idStazione) {
}
