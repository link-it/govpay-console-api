package it.govpay.console.pendenza;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Costruisce QR-Code e bar-code dell'avviso pagoPA replicando byte-per-byte
 * le funzioni omonime di {@code it.govpay.core.utils.IuvUtils} di V1
 * (govpay-381/.../jars/core/.../IuvUtils.java).
 *
 * <p>Riferimenti normativi:
 * <ul>
 *   <li>QR-Code "version 002": <em>"L'Avviso di pagamento analogico nel
 *       sistema pagoPA"</em> §2.1;</li>
 *   <li>Bar-Code: <em>"Guida Tecnica di Adesione PA 3.8"</em> p. 25.</li>
 * </ul>
 */
public final class IuvUtils {

    private IuvUtils() {
    }

    private static final DecimalFormat IMPORTO_FORMATTER =
            new DecimalFormat("00.00", new DecimalFormatSymbols(Locale.ENGLISH));

    public static byte[] buildQrCode002(String codDominio,
                                        int auxDigit,
                                        int applicationCode,
                                        String iuv,
                                        BigDecimal importoTotale,
                                        String numeroAvviso) {
        String qrCode;
        String importo = IMPORTO_FORMATTER.format(importoTotale).replace(".", "");
        if (numeroAvviso == null) {
            if (auxDigit == 0) {
                qrCode = "PAGOPA|002|0" + String.format("%02d", applicationCode) + iuv
                        + "|" + codDominio + "|" + importo;
            } else {
                qrCode = "PAGOPA|002|" + auxDigit + iuv + "|" + codDominio + "|" + importo;
            }
        } else {
            qrCode = "PAGOPA|002|" + numeroAvviso + "|" + codDominio + "|" + importo;
        }
        return qrCode.getBytes();
    }

    public static String buildBarCode(String gln,
                                      int auxDigit,
                                      int applicationCode,
                                      String iuv,
                                      BigDecimal importoTotale,
                                      String numeroAvviso) {
        String payToLoc = "415";
        String refNo = "8020";
        String amount = "3902";
        String importo = IMPORTO_FORMATTER.format(importoTotale).replace(".", "");

        if (numeroAvviso == null) {
            if (auxDigit == 3) {
                return payToLoc + gln + refNo + "3" + iuv + amount + importo;
            }
            return payToLoc + gln + refNo + "0" + String.format("%02d", applicationCode)
                    + iuv + amount + importo;
        }
        return payToLoc + gln + refNo + numeroAvviso + amount + importo;
    }
}
