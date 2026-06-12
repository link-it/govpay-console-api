package it.govpay.console.pendenza;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class IuvUtilsTest {

    private static final String COD_DOMINIO = "12345678901";
    private static final String GLN = "0123456789012";
    private static final String IUV = "1234567890123";
    private static final String NUMERO_AVVISO = "012345678901234567";
    // 123.45 → formatted "123.45" → senza punto "12345"
    private static final BigDecimal IMPORTO = new BigDecimal("123.45");
    private static final String IMPORTO_FORMATTED = "12345";

    @Test
    void qrCodeWithNumeroAvviso() {
        byte[] result = IuvUtils.buildQrCode002(COD_DOMINIO, 0, 1, IUV, IMPORTO, NUMERO_AVVISO);
        assertThat(new String(result))
                .isEqualTo("PAGOPA|002|" + NUMERO_AVVISO + "|" + COD_DOMINIO + "|" + IMPORTO_FORMATTED);
    }

    @Test
    void qrCodeWithoutNumeroAvvisoAuxDigit0() {
        byte[] result = IuvUtils.buildQrCode002(COD_DOMINIO, 0, 7, IUV, IMPORTO, null);
        assertThat(new String(result))
                .isEqualTo("PAGOPA|002|007" + IUV + "|" + COD_DOMINIO + "|" + IMPORTO_FORMATTED);
    }

    @Test
    void qrCodeWithoutNumeroAvvisoAuxDigitNon0() {
        byte[] result = IuvUtils.buildQrCode002(COD_DOMINIO, 3, 7, IUV, IMPORTO, null);
        assertThat(new String(result))
                .isEqualTo("PAGOPA|002|3" + IUV + "|" + COD_DOMINIO + "|" + IMPORTO_FORMATTED);
    }

    @Test
    void barCodeWithNumeroAvviso() {
        String result = IuvUtils.buildBarCode(GLN, 0, 1, IUV, IMPORTO, NUMERO_AVVISO);
        assertThat(result).isEqualTo("415" + GLN + "8020" + NUMERO_AVVISO + "3902" + IMPORTO_FORMATTED);
    }

    @Test
    void barCodeWithoutNumeroAvvisoAuxDigit3() {
        String result = IuvUtils.buildBarCode(GLN, 3, 7, IUV, IMPORTO, null);
        assertThat(result).isEqualTo("415" + GLN + "8020" + "3" + IUV + "3902" + IMPORTO_FORMATTED);
    }

    @Test
    void barCodeWithoutNumeroAvvisoAuxDigit0() {
        String result = IuvUtils.buildBarCode(GLN, 0, 7, IUV, IMPORTO, null);
        assertThat(result).isEqualTo("415" + GLN + "8020" + "007" + IUV + "3902" + IMPORTO_FORMATTED);
    }

    @Test
    void importoZeroIsFormattedAsZeroZero() {
        byte[] result = IuvUtils.buildQrCode002(COD_DOMINIO, 0, 1, IUV, BigDecimal.ZERO, NUMERO_AVVISO);
        assertThat(new String(result)).endsWith("|0000");
    }
}
