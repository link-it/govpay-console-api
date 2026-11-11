package it.govpay.console.dominio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Verifica che un logo PNG o JPEG si possa decodificare per intero (issue #124).
 * I magic bytes riconoscono solo l'intestazione: un file troncato o corrotto
 * passerebbe il controllo del tipo e fallirebbe solo in stampa.
 * <p>
 * Il JPEG si legge con {@link ImageReader#readRaster}: un JPEG CMYK, che il
 * motore di stampa gestisce, con {@link ImageReader#read} fallirebbe per la
 * conversione in RGB. Gli avvisi del lettore (ad esempio "Premature end of JPEG
 * file", con cui il JPEG troncato viene completato in grigio) valgono come errori.
 */
public final class LogoRasterVerifier {

    private static final int MAX_LUNGHEZZA_CAUSA = 200;

    private LogoRasterVerifier() {
    }

    /**
     * @param content     i byte dell'immagine
     * @param contentType il tipo riconosciuto dai magic bytes, {@code image/png} o {@code image/jpeg}
     * @throws LogoNonValidoException se l'immagine non si decodifica per intero o non ha dimensioni
     */
    public static void verifica(byte[] content, String contentType) throws LogoNonValidoException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(contentType);
        if (!readers.hasNext()) {
            throw new IllegalStateException("Nessun lettore ImageIO per " + contentType);
        }
        ImageReader reader = readers.next();
        List<String> avvisi = new ArrayList<>();
        reader.addIIOReadWarningListener((source, warning) -> avvisi.add(warning));
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                throw new LogoNonValidoException("l'immagine non ha dimensioni valide");
            }
            if (LogoMimeDetector.IMAGE_JPEG.equals(contentType)) {
                reader.readRaster(0, null);
            } else {
                reader.read(0);
            }
        } catch (IOException | RuntimeException e) {
            throw new LogoNonValidoException(causa(e));
        } finally {
            reader.dispose();
        }
        if (!avvisi.isEmpty()) {
            throw new LogoNonValidoException(limita(avvisi.get(0)));
        }
    }

    /** Messaggio dell'errore piu' interno: per ImageIO e' quello che descrive il problema. */
    private static String causa(Throwable e) {
        String messaggio = null;
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                messaggio = t.getMessage();
            }
        }
        return messaggio == null ? e.getClass().getSimpleName() : limita(messaggio);
    }

    private static String limita(String messaggio) {
        String causa = messaggio.replaceAll("\\s+", " ").trim();
        return causa.length() > MAX_LUNGHEZZA_CAUSA ? causa.substring(0, MAX_LUNGHEZZA_CAUSA) + "..." : causa;
    }

    /** Il logo non si decodifica: il messaggio e' la causa, da riportare all'operatore. */
    public static class LogoNonValidoException extends Exception {

        private static final long serialVersionUID = 1L;

        public LogoNonValidoException(String causa) {
            super(causa);
        }
    }
}
