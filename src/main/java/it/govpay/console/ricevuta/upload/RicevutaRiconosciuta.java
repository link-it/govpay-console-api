package it.govpay.console.ricevuta.upload;

/**
 * Esito del riconoscimento di una RT caricata da cruscotto:
 * il formato e la tupla {@code (idDominio, iuv, idRicevuta)} letta dal
 * contenuto, prima di qualunque normalizzazione/conversione pesante — serve
 * al pre-flight duplicato (§7) e all'audit (§F) il prima possibile.
 *
 * <p>{@code idDominio}/{@code iuv}/{@code idRicevuta} possono essere
 * {@code null} se il campo sorgente manca: la validazione di merito (400 sui
 * campi obbligatori) resta a valle, in {@link RicevutaJsonValidator} per il
 * ramo JSON o nel fault di core per il ramo XML.
 */
public record RicevutaRiconosciuta(RicevutaFormato formato, String idDominio, String iuv, String idRicevuta) {
}
