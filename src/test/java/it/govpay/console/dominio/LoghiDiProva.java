package it.govpay.console.dominio;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Loghi di prova per il caricamento (issue #124), con gli stessi casi della
 * testsuite di stampa del core ({@code LoghiDiProva} di link-it/govpay#902).
 * I formati che ImageIO non sa scrivere (JPEG CMYK, GIF animata, WebP) sono
 * file in {@code src/test/resources/loghi}, presi dal core.
 */
final class LoghiDiProva {

    /** Esito atteso della {@code PUT /domini/{id}/logo}. */
    enum Esito {
        ACCETTATO(200), TIPO_NON_AMMESSO(415), NON_LEGGIBILE(422);

        final int status;

        Esito(int status) {
            this.status = status;
        }
    }

    record Logo(String nome, byte[] contenuto, Esito esito) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static final int LARGHEZZA = 240;
    private static final int ALTEZZA = 120;

    private LoghiDiProva() {
    }

    static List<Logo> tutti() {
        byte[] png = png(raster(BufferedImage.TYPE_INT_RGB));
        byte[] jpeg = scrivi(raster(BufferedImage.TYPE_INT_RGB), "jpeg");
        return List.of(
                // PNG
                new Logo("png-rgb", png, Esito.ACCETTATO),
                new Logo("png-rgba", png(trasparente()), Esito.ACCETTATO),
                new Logo("png-palette-8bit", png(indicizzata(false)), Esito.ACCETTATO),
                new Logo("png-grigi", png(raster(BufferedImage.TYPE_BYTE_GRAY)), Esito.ACCETTATO),
                new Logo("png-16bit", png(raster(BufferedImage.TYPE_USHORT_GRAY)), Esito.ACCETTATO),
                // JPEG
                new Logo("jpeg-rgb", jpeg, Esito.ACCETTATO),
                new Logo("jpeg-grigi", scrivi(raster(BufferedImage.TYPE_BYTE_GRAY), "jpeg"), Esito.ACCETTATO),
                new Logo("jpeg-progressivo", jpegProgressivo(), Esito.ACCETTATO),
                new Logo("jpeg-cmyk", risorsa("logo-cmyk.jpg"), Esito.ACCETTATO),
                // altri formati raster: non ammessi al caricamento
                new Logo("gif", scrivi(indicizzata(false), "gif"), Esito.TIPO_NON_AMMESSO),
                new Logo("gif-animata", risorsa("logo-animata.gif"), Esito.TIPO_NON_AMMESSO),
                new Logo("bmp-24bit", scrivi(raster(BufferedImage.TYPE_3BYTE_BGR), "bmp"), Esito.TIPO_NON_AMMESSO),
                new Logo("tiff", scrivi(raster(BufferedImage.TYPE_INT_RGB), "tiff"), Esito.TIPO_NON_AMMESSO),
                new Logo("webp", risorsa("logo.webp"), Esito.TIPO_NON_AMMESSO),
                // SVG: non ammessi al caricamento
                svg("svg-semplice", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"240\" height=\"120\"><rect width=\"200\" height=\"80\" fill=\"#0066cc\"/></svg>"),
                svg("svg-png-incorporato", "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"240\" height=\"120\"><image width=\"240\" height=\"120\" xlink:href=\"data:image/png;base64,"
                        + java.util.Base64.getEncoder().encodeToString(png) + "\"/></svg>"),
                svg("svg-immagine-esterna", "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"240\" height=\"120\"><image width=\"240\" height=\"120\" xlink:href=\"https://example.org/logo.png\"/></svg>"),
                // file troncati o corrotti
                new Logo("png-troncato", Arrays.copyOf(png, png.length / 2), Esito.NON_LEGGIBILE),
                new Logo("jpeg-troncato", Arrays.copyOf(jpeg, jpeg.length / 2), Esito.NON_LEGGIBILE),
                new Logo("png-intestazione-e-byte-casuali", intestazionePngEByteCasuali(), Esito.NON_LEGGIBILE),
                new Logo("byte-casuali", casuali(), Esito.TIPO_NON_AMMESSO));
    }

    private static Logo svg(String nome, String svg) {
        return new Logo(nome, svg.getBytes(StandardCharsets.UTF_8), Esito.TIPO_NON_AMMESSO);
    }

    private static BufferedImage raster(int tipo) {
        BufferedImage img = new BufferedImage(LARGHEZZA, ALTEZZA, tipo);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0, 102, 204), LARGHEZZA, ALTEZZA, Color.WHITE));
        g.fillRect(0, 0, LARGHEZZA, ALTEZZA);
        g.setColor(Color.BLACK);
        g.drawString("Ente", 10, 60);
        g.dispose();
        return img;
    }

    private static BufferedImage trasparente() {
        BufferedImage img = new BufferedImage(LARGHEZZA, ALTEZZA, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 102, 204, 128));
        g.fillOval(0, 0, LARGHEZZA, ALTEZZA);
        g.dispose();
        return img;
    }

    private static BufferedImage indicizzata(boolean trasparente) {
        byte[] r = {0, (byte) 255, 0};
        byte[] g = {102, (byte) 255, 0};
        byte[] b = {(byte) 204, (byte) 255, 0};
        IndexColorModel palette = trasparente ? new IndexColorModel(8, 3, r, g, b, 1) : new IndexColorModel(8, 3, r, g, b);
        BufferedImage img = new BufferedImage(LARGHEZZA, ALTEZZA, BufferedImage.TYPE_BYTE_INDEXED, palette);
        Graphics2D gr = img.createGraphics();
        gr.setColor(Color.WHITE);
        gr.fillRect(0, 0, LARGHEZZA, ALTEZZA);
        gr.setColor(new Color(0, 102, 204));
        gr.fillRect(20, 20, 200, 80);
        gr.dispose();
        return img;
    }

    private static byte[] jpegProgressivo() {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(raster(BufferedImage.TYPE_INT_RGB), null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] intestazionePngEByteCasuali() {
        byte[] firma = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] casuali = casuali();
        byte[] b = Arrays.copyOf(firma, firma.length + casuali.length);
        System.arraycopy(casuali, 0, b, firma.length, casuali.length);
        return b;
    }

    private static byte[] casuali() {
        byte[] b = new byte[2048];
        new Random(124).nextBytes(b);
        return b;
    }

    private static byte[] png(BufferedImage img) {
        return scrivi(img, "png");
    }

    private static byte[] scrivi(BufferedImage img, String formato) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(img, formato, out)) {
                throw new IllegalStateException("Nessun writer ImageIO per il formato " + formato);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] risorsa(String nome) {
        try (InputStream is = LoghiDiProva.class.getResourceAsStream("/loghi/" + nome)) {
            if (is == null) {
                throw new IllegalStateException("Risorsa di test mancante: /loghi/" + nome);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
