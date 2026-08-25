package it.govpay.console.riconciliazione;

import it.govpay.console.model.Riconciliazione;

/**
 * Esito dell'upsert: {@code accettata=true} ⇔ 202 (nuova o riaccodata da
 * ERRORE), false ⇔ 200 idempotente. {@code idIncasso} è la PK tecnica
 * dell'{@code Incasso} scritto/letto, usata come {@code idOggetto}
 * dell'audit in {@link RiconciliazioneWriteService}.
 */
public record UpsertResult(Riconciliazione body, boolean accettata, Long idIncasso) {
}
