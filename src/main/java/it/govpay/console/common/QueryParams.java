package it.govpay.console.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Resa dei valori destinati alla query string delle chiamate verso i servizi
 * esterni (GDE, microservizi batch, ...), dove il {@code toString()} di default
 * non sempre sopravvive al viaggio.
 */
public final class QueryParams {

    private QueryParams() {
    }

    /**
     * Un istante da mettere in query string va normalizzato in UTC, cosi' che
     * {@link OffsetDateTime#toString()} lo renda con la {@code Z} finale.
     *
     * <p>Non e' una preferenza di formato. Con un offset diverso da UTC la resa
     * contiene un {@code +} (es. {@code +02:00}), che e' un sub-delimiter legale
     * dentro una query string (RFC 3986): {@code UriComponentsBuilder} non lo
     * percent-encoda, e all'altro capo Tomcat lo decodifica come uno spazio.
     * Il valore arriva percio' spezzato ({@code 2026-09-24T17:04:11 02:00}), il
     * binding su {@code OffsetDateTime} fallisce e la richiesta torna 400 — che
     * i nostri facade, non distinguendo i 4xx, ripresentano come un 502 opaco.
     *
     * <p>L'istante non cambia: cambia il rappresentante scelto per scriverlo.
     */
    public static String istanteUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }
}
