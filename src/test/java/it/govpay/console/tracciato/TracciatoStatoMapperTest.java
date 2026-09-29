package it.govpay.console.tracciato;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import it.govpay.console.model.StatoTracciatoPendenza;

/**
 * Mappatura dello stato grezzo di {@code tracciati.stato} nello stato REST.
 *
 * Il caso che conta e' l'ultimo: prima uno stato fuori vocabolario sollevava
 * {@code IllegalStateException}, e poiche' la mappatura gira dentro uno stream
 * sui risultati, una sola riga anomala faceva rispondere 500 all'intera pagina.
 */
class TracciatoStatoMapperTest {

    @Test
    void completatoConCaricamentoOkEEseguito() {
        assertThat(TracciatoStatoMapper.toRest("COMPLETATO", "CARICAMENTO_OK"))
                .isEqualTo(StatoTracciatoPendenza.ESEGUITO);
    }

    @Test
    void completatoConAltroStepEEseguitoConErrori() {
        assertThat(TracciatoStatoMapper.toRest("COMPLETATO", "CARICAMENTO_KO"))
                .isEqualTo(StatoTracciatoPendenza.ESEGUITO_CON_ERRORI);
    }

    @Test
    void elaborazioneConStepNuovoEInAttesa() {
        assertThat(TracciatoStatoMapper.toRest("ELABORAZIONE", "NUOVO"))
                .isEqualTo(StatoTracciatoPendenza.IN_ATTESA);
    }

    @Test
    void elaborazioneConAltroStepEInElaborazione() {
        assertThat(TracciatoStatoMapper.toRest("ELABORAZIONE", "IN_CARICAMENTO"))
                .isEqualTo(StatoTracciatoPendenza.IN_ELABORAZIONE);
    }

    @Test
    void scartatoEIndipendenteDalloStep() {
        assertThat(TracciatoStatoMapper.toRest("SCARTATO", null))
                .isEqualTo(StatoTracciatoPendenza.SCARTATO);
    }

    @Test
    void inStampaEElaborazioneStampa() {
        assertThat(TracciatoStatoMapper.toRest("IN_STAMPA", null))
                .isEqualTo(StatoTracciatoPendenza.ELABORAZIONE_STAMPA);
    }

    @Test
    void statoFuoriVocabolarioNonSollevaEccezione() {
        assertThat(TracciatoStatoMapper.toRest("STATO_CHE_IL_CORE_NON_SCRIVE", null))
                .isEqualTo(StatoTracciatoPendenza.NON_RICONOSCIUTO);
    }

    @Test
    void nonRiconosciutoNonHaUnValoreDiColonnaCorrispondente() {
        assertThat(TracciatoStatoMapper.statoDbFor(StatoTracciatoPendenza.NON_RICONOSCIUTO)).isNull();
        assertThat(TracciatoStatoMapper.beanDatiLikePattern(StatoTracciatoPendenza.NON_RICONOSCIUTO)).isNull();
    }

    @Test
    void gliStatiNotiSonoIQuattroScrittiDalCore() {
        assertThat(TracciatoStatoMapper.statiDbNoti())
                .containsExactlyInAnyOrder("ELABORAZIONE", "COMPLETATO", "SCARTATO", "IN_STAMPA");
    }
}
