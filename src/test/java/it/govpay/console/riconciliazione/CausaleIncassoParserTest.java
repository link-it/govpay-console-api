package it.govpay.console.riconciliazione;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test su casi reali del parser (port di {@code IncassoUtils} V1):
 * causali cumulative (con e senza segmento {@code TXT}), causali singole
 * ({@code RFS}/{@code RFB}) e causali non conformi.
 */
class CausaleIncassoParserTest {

    @Test
    void cumulativoSemplice() {
        String causale = "PUR/LGPE-RIVERSAMENTO/URI/2026-06-20-XYZ-001";
        assertThat(CausaleIncassoParser.getRiferimentoIncassoCumulativo(causale))
                .isEqualTo("2026-06-20-XYZ-001");
        assertThat(CausaleIncassoParser.getRiferimentoIncassoSingolo(causale)).isNull();
    }

    @Test
    void cumulativoConSegmentoTxt() {
        String causale = "PUR/LGPE-RIVERSAMENTO/TXT/1/URI/2026-06-20-FLOW-ABC";
        assertThat(CausaleIncassoParser.getRiferimentoIncassoCumulativo(causale))
                .isEqualTo("2026-06-20-FLOW-ABC");
    }

    @Test
    void singoloRfs() {
        String causale = "RFS/123456789012345";
        assertThat(CausaleIncassoParser.getRiferimentoIncassoSingolo(causale))
                .isEqualTo("123456789012345");
        assertThat(CausaleIncassoParser.getRiferimentoIncassoCumulativo(causale)).isNull();
    }

    @Test
    void singoloRfb() {
        String causale = "RFB/RF18123456789012345";
        assertThat(CausaleIncassoParser.getRiferimentoIncassoSingolo(causale))
                .isEqualTo("RF18123456789012345");
    }

    @Test
    void causaleNonConformeRitornaEntrambiNull() {
        String causale = "PAGAMENTO GENERICO SENZA RIFERIMENTI";
        assertThat(CausaleIncassoParser.getRiferimentoIncassoCumulativo(causale)).isNull();
        assertThat(CausaleIncassoParser.getRiferimentoIncassoSingolo(causale)).isNull();
    }
}
