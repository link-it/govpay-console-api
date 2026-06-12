package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import it.govpay.console.model.LinguaSecondaria;
import it.govpay.stampe.client.model.Languages;

class AvvisoPdfPayloadMapperTest {

    @Test
    void deMapsToClientDE() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.DE)).isEqualTo(Languages.DE);
    }

    @Test
    void enMapsToClientEN() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.EN)).isEqualTo(Languages.EN);
    }

    @Test
    void frMapsToClientFR() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.FR)).isEqualTo(Languages.FR);
    }

    @Test
    void slMapsToClientSL() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.SL)).isEqualTo(Languages.SL);
    }

    @Test
    void noneMapsToNull() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.NONE)).isNull();
    }

    @Test
    void nullMapsToNull() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(null)).isNull();
    }
}
