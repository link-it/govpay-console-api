package it.govpay.console.dominio;

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
        Collection<?> mimeTypes = MimeUtil.getMimeTypes(content);
        if (mimeTypes.isEmpty()) {
            return null;
        }
        return MimeUtil.getFirstMimeType(mimeTypes.toString()).toString();
    }

    public static boolean isSupported(String contentType) {
        return SUPPORTED.contains(contentType);
    }

    public static Set<String> supportedTypes() {
        return SUPPORTED;
    }
}
