package it.govpay.console.dominio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import eu.medsea.mimeutil.MimeUtil;

/**
 * Determina il content-type di un logo dai magic bytes del contenuto, senza
 * persistere alcuna informazione di tipo: il logo viene memorizzato come byte
 * grezzi e il tipo e' ricalcolato a ogni lettura.
 */
public final class LogoMimeDetector {

    public static final String IMAGE_PNG = "image/png";
    public static final String IMAGE_JPEG = "image/jpeg";
    /**
     * Riconosciuto per i loghi gia' in archivio (console legacy, API
     * Backoffice), ma non ammesso al caricamento (issue #124).
     */
    public static final String IMAGE_SVG = "image/svg+xml";

    /** Byte iniziali in cui cercare l'elemento radice: prima possono esserci dichiarazione XML, DOCTYPE e commenti. */
    private static final int SVG_PREFISSO = 4096;

    private static final Set<String> SUPPORTED = Set.of(IMAGE_PNG, IMAGE_JPEG);

    static {
        MimeUtil.registerMimeDetector("eu.medsea.mimeutil.detector.MagicMimeMimeDetector");
    }

    private LogoMimeDetector() {
    }

    /**
     * @return il content-type riconosciuto dai magic bytes, o {@code null} se il
     *         contenuto e' vuoto o non riconoscibile.
     */
    public static String detect(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        if (isSvg(content)) {
            return IMAGE_SVG;
        }
        Collection<?> mimeTypes = MimeUtil.getMimeTypes(content);
        if (mimeTypes.isEmpty()) {
            return null;
        }
        return MimeUtil.getFirstMimeType(mimeTypes.toString()).toString();
    }

    /**
     * SVG: testo XML (UTF-8 o UTF-16, con o senza BOM) che inizia con un tag e
     * contiene l'elemento {@code <svg} nei primi {@value #SVG_PREFISSO} byte.
     */
    static boolean isSvg(byte[] content) {
        byte[] prefisso = Arrays.copyOf(content, Math.min(content.length, SVG_PREFISSO));
        String testo = new String(prefisso, charsetOf(prefisso)).replace("\uFEFF", "").stripLeading();
        return testo.startsWith("<") && testo.contains("<svg");
    }

    private static Charset charsetOf(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return StandardCharsets.UTF_8;
    }

    public static boolean isSupported(String contentType) {
        return SUPPORTED.contains(contentType);
    }

    public static Set<String> supportedTypes() {
        return SUPPORTED;
    }
}
