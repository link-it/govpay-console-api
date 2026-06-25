package it.govpay.console.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class CausaleVersamentoDecoderTest {

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void semplice01() {
        String enc = "01 " + b64("TARI 2026 - saldo");
        assertThat(CausaleVersamentoDecoder.decodeSimple(enc)).isEqualTo("TARI 2026 - saldo");
    }

    @Test
    void spezzoni02RitornaIlPrimo() {
        String enc = "02 " + b64("primo spezzone") + " " + b64("secondo");
        assertThat(CausaleVersamentoDecoder.decodeSimple(enc)).isEqualTo("primo spezzone");
    }

    @Test
    void strutturati03ImportoESpezzone() {
        String enc = "03 " + b64("rata gennaio") + " " + b64("12.0");
        assertThat(CausaleVersamentoDecoder.decodeSimple(enc)).isEqualTo("12.0: rata gennaio");
    }

    @Test
    void valoreNonCodificatoRitornatoVerbatim() {
        assertThat(CausaleVersamentoDecoder.decodeSimple("Causale in chiaro"))
                .isEqualTo("Causale in chiaro");
    }

    @Test
    void base64MalformatoFallbackGrezzo() {
        String enc = "01 !!!non-base64!!!";
        assertThat(CausaleVersamentoDecoder.decodeSimple(enc)).isEqualTo(enc);
    }

    @Test
    void nullEBlankRitornanoNull() {
        assertThat(CausaleVersamentoDecoder.decodeSimple(null)).isNull();
        assertThat(CausaleVersamentoDecoder.decodeSimple("   ")).isNull();
    }
}
