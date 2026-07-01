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

    private static byte[] image(String format) throws IOException {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) {
            throw new IllegalStateException("Nessun writer ImageIO per il formato " + format);
        }
        return out.toByteArray();
    }
}
