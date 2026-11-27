package it.govpay.console.common;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import it.govpay.common.utils.DateTimePatterns;

/**
 * Estremi di giorno per i filtri sulle colonne con data e ora: il giorno e' inteso nel fuso
 * delle date sul database, {@value DateTimePatterns#DEFAULT_TIME_ZONE}.
 */
public final class GiornoDatabase {

    private static final ZoneId FUSO = ZoneId.of(DateTimePatterns.DEFAULT_TIME_ZONE);

    private GiornoDatabase() {
    }

    /** Primo istante del giorno indicato. */
    public static OffsetDateTime inizio(LocalDate giorno) {
        return giorno.atStartOfDay(FUSO).toOffsetDateTime();
    }
}
