package it.govpay.console.pendenza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import it.govpay.console.entity.UnitaOperativa;
import it.govpay.console.model.StatoPendenza;
import it.govpay.console.model.TipoContabilita;
import it.govpay.console.model.UnitaOperativaRef;
import it.govpay.console.model.VocePendenzaBolloTelematico;
import it.govpay.console.model.VocePendenzaEntrataAnagrafica;
import it.govpay.console.model.VocePendenzaIncassoDiretto;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.entity.VocePendenza;

// I test vivono tutti nelle classi @Nested, che raggruppano i comportamenti del mapper:
// la classe esterna non ne dichiara di propri, ma la copertura c'e' — S2187 qui e' un
// falso positivo.
@SuppressWarnings("java:S2187")
class PendenzaMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final OffsetDateTime ADESSO = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final PendenzaMapper mapper = new PendenzaMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("mapStato del modello v3")
    class StatoModelloV3 {

        private PosizioneDebitoria posizione(LocalDate dataPubblicazione) {
            PosizioneDebitoria posizione = new PosizioneDebitoria();
            posizione.setDataPubblicazione(dataPubblicazione);
            return posizione;
        }

        @ParameterizedTest
        @CsvSource({
                "ESEGUITO, ESEGUITO",
                "PARZIALMENTE_ESEGUITO, PARZIALMENTE_ESEGUITO",
                "ANNULLATO, ANNULLATO",
                "ESEGUITO_ALTRO_CANALE, ESEGUITO_ALTRO_CANALE",
                "ANOMALO, ANOMALO",
                "ESEGUITO_SENZA_RPT, ESEGUITO_SENZA_RPT",
                "INCASSATO, INCASSATO"
        })
        @DisplayName("Gli stati non derivati restano invariati, anche con scadenza passata o pubblicazione futura")
        void statiNonDerivati(it.govpay.pendenze.model.StatoPendenza stato, StatoPendenza atteso) {
            assertEquals(atteso, mapper.mapStato(stato, ADESSO.minusDays(1), posizione(LocalDate.of(2026, 7, 1))));
        }

        @Test
        @DisplayName("NON_ESEGUITO con scadenza futura e posizione pubblicata -> NON_ESEGUITO")
        void nonEseguito() {
            assertEquals(StatoPendenza.NON_ESEGUITO, mapper.mapStato(
                    it.govpay.pendenze.model.StatoPendenza.NON_ESEGUITO, ADESSO.plusDays(1), posizione(null)));
        }

        @Test
        @DisplayName("NON_ESEGUITO con scadenza passata -> SCADUTO")
        void scaduto() {
            assertEquals(StatoPendenza.SCADUTO, mapper.mapStato(
                    it.govpay.pendenze.model.StatoPendenza.NON_ESEGUITO, ADESSO.minusDays(1), posizione(null)));
        }

        @Test
        @DisplayName("NON_ESEGUITO con pubblicazione futura -> NON_PUBBLICATA, anche se la scadenza e' passata")
        void nonPubblicataPrevaleSuScaduto() {
            assertEquals(StatoPendenza.NON_PUBBLICATA, mapper.mapStato(
                    it.govpay.pendenze.model.StatoPendenza.NON_ESEGUITO, ADESSO.minusDays(1),
                    posizione(LocalDate.of(2026, 6, 16))));
        }

        @Test
        @DisplayName("La pubblicazione nel giorno corrente vale come pubblicata")
        void pubblicataNelGiornoCorrente() {
            assertEquals(StatoPendenza.NON_ESEGUITO, mapper.mapStato(
                    it.govpay.pendenze.model.StatoPendenza.NON_ESEGUITO, ADESSO.plusDays(1),
                    posizione(LocalDate.of(2026, 6, 15))));
        }

        @Test
        @DisplayName("Stato null -> null")
        void statoNull() {
            assertNull(mapper.mapStato((it.govpay.pendenze.model.StatoPendenza) null, null, null));
        }
    }

    @Nested
    @DisplayName("unita' operativa")
    class UnitaOperativaEc {

        private UnitaOperativa uo(String codUo, String denominazione) {
            UnitaOperativa uo = new UnitaOperativa();
            uo.setCodUo(codUo);
            uo.setUoDenominazione(denominazione);
            return uo;
        }

        @Test
        @DisplayName("L'unita' operativa EC (ente creditore) non si espone")
        void ecNonEsposta() {
            assertNull(PendenzaMapper.mapUnitaOperativa(uo("EC", "Ente creditore")));
        }

        @Test
        @DisplayName("Assente -> null")
        void assente() {
            assertNull(PendenzaMapper.mapUnitaOperativa(null));
        }

        @Test
        @DisplayName("Una unita' operativa specifica si espone con codice e denominazione")
        void specifica() {
            UnitaOperativaRef ref = PendenzaMapper.mapUnitaOperativa(uo("UO1", "Ufficio tributi"));
            assertNotNull(ref);
            assertEquals("UO1", ref.getIdUnitaOperativa());
            assertEquals("Ufficio tributi", ref.getRagioneSociale());
        }
    }

    @Nested
    @DisplayName("dettaglio della voce")
    class DettaglioVoce {

        private final PendenzaAnagrafiche.Voci anagrafiche = new PendenzaAnagrafiche.Voci(
                Map.of(), Map.of(7L, "TARI"), Map.of(11L, "IT60X0542811101000000123456",
                        12L, "IT60X0542811101000000654321"));

        @Test
        @DisplayName("Riferimento a entrata: codice dell'entrata in anagrafica")
        void entrataAnagrafica() {
            VocePendenza voce = new VocePendenza();
            voce.setIdTributo(7L);

            VocePendenzaEntrataAnagrafica dettaglio = assertInstanceOf(VocePendenzaEntrataAnagrafica.class,
                    PendenzaMapper.mapDettaglioVoce(voce, anagrafiche));
            assertEquals("TARI", dettaglio.getCodEntrata());
        }

        @Test
        @DisplayName("Incasso diretto: IBAN di accredito e di appoggio, tipo e codice contabilita'")
        void incassoDiretto() {
            VocePendenza voce = new VocePendenza();
            voce.setIdIbanAccredito(11L);
            voce.setIdIbanAppoggio(12L);
            voce.setTipoContabilita(it.govpay.common.entity.TipoContabilita.SIOPE);
            voce.setCodiceContabilita("3321");

            VocePendenzaIncassoDiretto dettaglio = assertInstanceOf(VocePendenzaIncassoDiretto.class,
                    PendenzaMapper.mapDettaglioVoce(voce, anagrafiche));
            assertEquals("IT60X0542811101000000123456", dettaglio.getIbanAccredito());
            assertEquals("IT60X0542811101000000654321", dettaglio.getIbanAppoggio());
            assertEquals(TipoContabilita.SIOPE, dettaglio.getTipoContabilita());
            assertEquals("3321", dettaglio.getCodiceContabilita());
        }

        @Test
        @DisplayName("Bollo telematico: hash e provincia di residenza")
        void bollo() {
            VocePendenza voce = new VocePendenza();
            voce.setTipoBollo("01");
            voce.setHashDocumento("aGFzaA==");
            voce.setProvinciaResidenza("RM");

            VocePendenzaBolloTelematico dettaglio = assertInstanceOf(VocePendenzaBolloTelematico.class,
                    PendenzaMapper.mapDettaglioVoce(voce, anagrafiche));
            assertEquals("aGFzaA==", dettaglio.getHashDocumento());
            assertEquals("RM", dettaglio.getProvinciaResidenza());
        }

        @Test
        @DisplayName("Nessuna forma riconoscibile -> null")
        void nessunaForma() {
            assertNull(PendenzaMapper.mapDettaglioVoce(new VocePendenza(), anagrafiche));
        }
    }
}
