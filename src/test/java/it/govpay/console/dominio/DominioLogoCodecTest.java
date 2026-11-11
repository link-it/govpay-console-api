package it.govpay.console.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class DominioLogoCodecTest {

    @Test
    void encodeStoresBase64TextBytes() throws IOException {
        byte[] png = image("png");

        byte[] stored = DominioLogoCodec.encode(png);

        assertThat(new String(stored, StandardCharsets.UTF_8))
                .isEqualTo(Base64.getEncoder().encodeToString(png));
    }

    @Test
    void decodeReturnsRawImageBytes() throws IOException {
        byte[] png = image("png");
        byte[] stored = Base64.getEncoder().encodeToString(png).getBytes(StandardCharsets.UTF_8);

        assertThat(DominioLogoCodec.decode(stored)).isEqualTo(png);
    }

    @Test
    void encodeDecodeRoundTrip() throws IOException {
        byte[] jpeg = image("jpeg");

        assertThat(DominioLogoCodec.decode(DominioLogoCodec.encode(jpeg))).isEqualTo(jpeg);
    }

    @Test
    void decodeToleratesDataUriPrefix() throws IOException {
        byte[] png = image("png");
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        byte[] stored = dataUri.getBytes(StandardCharsets.UTF_8);

        assertThat(DominioLogoCodec.decode(stored)).isEqualTo(png);
    }

    @Test
    void toDataUriReusesStoredBase64AndDetectsContentType() throws IOException {
        byte[] png = image("png");
        byte[] stored = DominioLogoCodec.encode(png);

        assertThat(DominioLogoCodec.toDataUri(stored))
                .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(png));
    }

    @Test
    void toDataUriEmptyForNoLogo() {
        assertThat(DominioLogoCodec.toDataUri(null)).isEmpty();
        assertThat(DominioLogoCodec.toDataUri(new byte[0])).isEmpty();
    }

    // --- Loghi gia' in archivio (issue #124) ---

    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/></svg>";

    @Test
    void leggiRiconosceSvgSempliceConBomConDichiarazioneEDoctype() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String dichiarazione = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- logo -->
                <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
                """;
        for (byte[] svg : java.util.List.of(
                SVG.getBytes(StandardCharsets.UTF_8),
                concat(bom, SVG.getBytes(StandardCharsets.UTF_8)),
                (dichiarazione + SVG).getBytes(StandardCharsets.UTF_8),
                ("\uFEFF" + SVG).getBytes(StandardCharsets.UTF_16BE),
                ("\uFEFF" + SVG).getBytes(StandardCharsets.UTF_16LE))) {
            var logo = DominioLogoCodec.leggi(DominioLogoCodec.encode(svg));
            assertThat(logo).isPresent();
            assertThat(logo.get().contentType()).isEqualTo(LogoMimeDetector.IMAGE_SVG);
            assertThat(logo.get().immagine()).isEqualTo(svg);
        }
        assertThat(LogoMimeDetector.isSupported(LogoMimeDetector.IMAGE_SVG)).isFalse();
    }

    @Test
    void leggiToleraGliACapoEIlDataUri() throws IOException {
        byte[] png = image("png");
        String mime = Base64.getMimeEncoder(16, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(png);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

        assertThat(DominioLogoCodec.leggi(mime.getBytes(StandardCharsets.UTF_8)).orElseThrow().immagine()).isEqualTo(png);
        assertThat(DominioLogoCodec.leggi(dataUri.getBytes(StandardCharsets.UTF_8)).orElseThrow().contentType())
                .isEqualTo(LogoMimeDetector.IMAGE_PNG);
    }

    @Test
    void leggiVuotoPerValoriCheNonSonoImmagini() {
        // il link della GET salvato dalle vecchie PUT dell'API Backoffice: Base64 formalmente valido
        assertThat(DominioLogoCodec.leggi("/domini/12345678901/logo".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(DominioLogoCodec.leggi("%%% non e' base64 %%%".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(DominioLogoCodec.leggi(DominioLogoCodec.encode("testo".getBytes(StandardCharsets.UTF_8)))).isEmpty();
        assertThat(DominioLogoCodec.leggi(null)).isEmpty();
    }

    @Test
    void toDataUriSvgEVuotoPerLogoNonLeggibile() {
        byte[] stored = DominioLogoCodec.encode(SVG.getBytes(StandardCharsets.UTF_8));

        assertThat(DominioLogoCodec.toDataUri(stored))
                .isEqualTo("data:image/svg+xml;base64," + new String(stored, StandardCharsets.UTF_8));
        assertThat(DominioLogoCodec.toDataUri("/domini/12345678901/logo".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) {
            throw new IllegalStateException("Nessun writer ImageIO per il formato " + format);
        }
        return out.toByteArray();
    }
}
