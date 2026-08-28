package it.govpay.console.pendenza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import it.govpay.console.model.StatoPendenza;

class PendenzaMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

    private final PendenzaMapper mapper = new PendenzaMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("mapStato - mapping diretti")
    class MappingDiretti {

        @ParameterizedTest
        @CsvSource({
                "ESEGUITA, PAGATA",
                "ESEGUITO, PAGATA",
                "ESEGUITO_ALTRO_CANALE, PAGATA",
                "ESEGUITO_SENZA_RPT, PAGATA",
                "ESEGUITA_PARZIALE, PAGATA_PARZIALE",
                "ESEGUITO_PARZIALE, PAGATA_PARZIALE",
                "INCASSATA, RICONCILIATA",
                "INCASSATO, RICONCILIATA",
                "ANNULLATA, ANNULLATA",
                "ANNULLATO, ANNULLATA",
                "ANOMALA, ANOMALA",
                "ANOMALO, ANOMALA"
        })
        @DisplayName("Stati grezzi V1 mappati sul valore V2 atteso, senza dipendenza da dataScadenza")
        void mappaStatiDiretti(String statoV1, StatoPendenza atteso) {
            assertEquals(atteso, mapper.mapStato(statoV1, null));
        }

        @Test
        @DisplayName("Stato sconosciuto -> ANOMALA (fallback)")
        void statoSconosciuto() {
            assertEquals(StatoPendenza.ANOMALA, mapper.mapStato("QUALCOSA_DI_INESISTENTE", null));
        }

        @Test
        @DisplayName("Stato null -> null")
        void statoNull() {
            assertNull(mapper.mapStato(null, null));
        }
    }

    @Nested
    @DisplayName("mapStato - derivazione SCADUTA")
    class DerivazioneScaduta {

        @Test
        @DisplayName("NON_ESEGUITO con dataScadenza passata -> SCADUTA")
        void nonEseguitoScaduto() {
            OffsetDateTime scadenzaPassata = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1);
            assertEquals(StatoPendenza.SCADUTA, mapper.mapStato("NON_ESEGUITO", scadenzaPassata));
        }

        @Test
        @DisplayName("NON_ESEGUITO con dataScadenza futura -> NON_PAGATA")
        void nonEseguitoNonScaduto() {
            OffsetDateTime scadenzaFutura = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1);
            assertEquals(StatoPendenza.NON_PAGATA, mapper.mapStato("NON_ESEGUITO", scadenzaFutura));
        }

        @Test
        @DisplayName("NON_ESEGUITO senza dataScadenza -> NON_PAGATA")
        void nonEseguitoSenzaScadenza() {
            assertEquals(StatoPendenza.NON_PAGATA, mapper.mapStato("NON_ESEGUITO", null));
        }

        @Test
        @DisplayName("Stati diversi da NON_ESEGUITO non sono influenzati da dataScadenza")
        void altriStatiIgnoranoScadenza() {
            OffsetDateTime scadenzaPassata = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1);
            assertEquals(StatoPendenza.PAGATA, mapper.mapStato("ESEGUITO", scadenzaPassata));
        }
    }
}
