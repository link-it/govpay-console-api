package it.govpay.console.ricevuta.upload;

/**
 * Formati di RT riconosciuti da {@link RicevutaFormatDetector} come
 * acquisibili da cruscotto. SANP 2.3.0 (radice {@code RT}) ed elementi
 * radice non riconosciuti non compaiono qui: il detector li rifiuta
 * direttamente con 422. Nomi allineati al vocabolario dell'issue (§C).
 */
public enum RicevutaFormato {
    JSON_PAGOPA,
    V2_2,
    V2
}
