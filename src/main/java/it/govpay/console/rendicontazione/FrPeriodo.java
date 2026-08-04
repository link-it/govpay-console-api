package it.govpay.console.rendicontazione;

import java.time.OffsetDateTime;

/**
 * Finestra temporale coperta da un flusso, derivata da
 * {@code MIN}/{@code MAX(rendicontazioni.data)} per quel {@code id_fr}: non è
 * un campo proprio di {@code fr}, va calcolato solo nel dettaglio.
 */
public record FrPeriodo(OffsetDateTime dataInizio, OffsetDateTime dataFine) {
}
