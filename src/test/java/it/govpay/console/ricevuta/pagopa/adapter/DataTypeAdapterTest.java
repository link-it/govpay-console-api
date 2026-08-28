package it.govpay.console.ricevuta.pagopa.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Fissa il comportamento degli adapter JAXB/Jackson usati per la (de)serializzazione
 * dei messaggi pagoPA (RPT/RT). Non c'era copertura: e' il prerequisito per toccarli.
 */
class DataTypeAdapterTest {

    // --- xs:gYear (YearAdapter) ---

    /**
     * Il binding e' su {@code xs:gYear}, la cui forma lessicale e' {@code YYYY} con
     * fuso opzionale. L'implementazione tollera anche una data completa.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2026", "2026Z", "2026+01:00", "2026-01-01", "2026-06-15+02:00"})
    void parseYearAccettaLeFormeDiGYearEDate(String value) {
        assertThat(DataTypeAdapter.parseYear(value)).isEqualTo(2026);
    }

    @Test
    void parseYearNullRestituisceNull() {
        assertThat(DataTypeAdapter.parseYear(null)).isNull();
    }

    /**
     * Anno negativo (era precedente): l'implementazione basata su {@code Calendar}
     * restituiva 2026, perche' {@code Calendar.YEAR} non porta il segno dell'era.
     * La versione {@code java.time} conserva il segno. Caso irraggiungibile per una
     * ricevuta pagoPA, ma la divergenza va scritta invece che scoperta.
     */
    @Test
    void parseYearConservaIlSegnoDellAnnoNegativo() {
        assertThat(DataTypeAdapter.parseYear("-2026")).isEqualTo(-2026);
    }

    /** Anni oltre le quattro cifre: ammessi da xs:gYear e accettati da entrambe le versioni. */
    @Test
    void parseYearAccettaAnniOltreQuattroCifre() {
        assertThat(DataTypeAdapter.parseYear("12026")).isEqualTo(12026);
    }

    /**
     * Valore non interpretabile: resta un'eccezione unchecked, quindi JAXB continua a
     * riportarla come errore di unmarshalling. Cambia il tipo concreto, da
     * {@code IllegalArgumentException} a {@code DateTimeParseException}.
     */
    @Test
    void parseYearNonInterpretabileSolleva() {
        assertThatThrownBy(() -> DataTypeAdapter.parseYear("non-un-anno"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void printYear() {
        assertThat(DataTypeAdapter.printYear(2026)).isEqualTo("2026");
        assertThat(DataTypeAdapter.printYear(null)).isNull();
    }

    // --- importi (DecimalAdapter) ---

    @Test
    void importoRoundTrip() {
        assertThat(DataTypeAdapter.parseImporto("12.34")).isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(DataTypeAdapter.printImporto(new BigDecimal("12.3"))).isEqualTo("12.30");
        assertThat(DataTypeAdapter.printImporto(new BigDecimal("1234567.891"))).isEqualTo("1234567.89");
    }

    // --- date/ore locali (DateAdapter, TimeAdapter, DateTimeAdapter) ---

    @Test
    void localDateConESenzaFuso() {
        assertThat(DataTypeAdapterCXF.parseLocalDate("2026-06-15")).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(DataTypeAdapterCXF.parseLocalDate("2026-06-15+02:00")).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(DataTypeAdapterCXF.parseLocalDate("2026-06-15Z")).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(DataTypeAdapterCXF.parseLocalDate(null)).isNull();
        assertThat(DataTypeAdapterCXF.parseLocalDate("")).isNull();
        assertThat(DataTypeAdapterCXF.printLocalDate(LocalDate.of(2026, 6, 15))).isEqualTo("2026-06-15");
        assertThat(DataTypeAdapterCXF.printLocalDate(null)).isNull();
    }

    @Test
    void localTimeConESenzaFuso() {
        assertThat(DataTypeAdapterCXF.parseLocalTime("10:30:00")).isEqualTo(LocalTime.of(10, 30));
        assertThat(DataTypeAdapterCXF.parseLocalTime("10:30:00+02:00")).isEqualTo(LocalTime.of(10, 30));
        assertThat(DataTypeAdapterCXF.parseLocalTime(null)).isNull();
        assertThat(DataTypeAdapterCXF.printLocalTime(LocalTime.of(10, 30))).isEqualTo("10:30:00");
        assertThat(DataTypeAdapterCXF.printLocalTime(null)).isNull();
    }

    @Test
    void localDateTimeConESenzaFuso() {
        assertThat(DataTypeAdapterCXF.parseLocalDateTime("2026-06-15T10:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 30));
        assertThat(DataTypeAdapterCXF.parseLocalDateTime("2026-06-15T10:30:00+02:00"))
                .isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 30));
        assertThat(DataTypeAdapterCXF.parseLocalDateTime(null)).isNull();
        assertThat(DataTypeAdapterCXF.printLocalDateTime(LocalDateTime.of(2026, 6, 15, 10, 30)))
                .isEqualTo("2026-06-15T10:30:00");
        assertThat(DataTypeAdapterCXF.printLocalDateTime(null)).isNull();
    }

    @Test
    void valoreNonInterpretabileSolleva() {
        assertThatThrownBy(() -> DataTypeAdapterCXF.parseLocalDate("non-una-data"))
                .isInstanceOf(RuntimeException.class);
    }
}
