package it.govpay.console.dominio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.common.auth.GovpayPasswordEncoder;
import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Operatore;
import it.govpay.console.entity.Utenza;
import it.govpay.console.repository.DominioRepository;
import it.govpay.console.repository.GpAuditRepository;
import it.govpay.console.repository.OperatoreRepository;
import it.govpay.console.repository.UtenzaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DominioLogoControllerIntegrationTest {

    private static final String PRINCIPAL = "operatore1";
    private static final String PASSWORD = "secret";
    private static final String ID_DOMINIO = "12345678901";

    private static final MediaType IMAGE_PNG = MediaType.valueOf("image/png");
    private static final MediaType IMAGE_JPEG = MediaType.valueOf("image/jpeg");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GovpayPasswordEncoder encoder;
    @Autowired
    private UtenzaRepository utenzaRepository;
    @Autowired
    private OperatoreRepository operatoreRepository;
    @Autowired
    private DominioRepository dominioRepository;
    @Autowired
    private GpAuditRepository gpAuditRepository;

    @BeforeEach
    void setup() {
        Utenza utenza = new Utenza();
        utenza.setPrincipal(PRINCIPAL);
        utenza.setPrincipalOriginale(PRINCIPAL);
        utenza.setAbilitato(true);
        utenza.setAutorizzazioneDominiStar(true);
        utenza.setAutorizzazioneTipiVersStar(true);
        utenza.setRuoli("OPERATORE");
        utenza.setPassword(encoder.encode(PASSWORD));
        utenzaRepository.save(utenza);

        Operatore op = new Operatore();
        op.setNome("Operatore Uno");
        op.setIdUtenza(utenza.getId());
        operatoreRepository.save(op);

        Dominio d = new Dominio();
        d.setCodDominio(ID_DOMINIO);
        d.setRagioneSociale("Comune Alfa");
        d.setAuxDigit(0);
        d.setAbilitato(true);
        d.setIntermediato(true);
        d.setScaricaFr(true);
        dominioRepository.save(d);
    }

    // --- Upload + download round-trip ---

    @Test
    void putThenGetReturnsSamePngBytes() throws Exception {
        byte[] png = image("png");

        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(png))
                .andExpect(status().isOk());

        byte[] body = mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "max-age=86400"))
                .andReturn().getResponse().getContentAsByteArray();

        Assertions.assertThat(body).isEqualTo(png);
    }

    @Test
    void putStoresBase64TextInColumn() throws Exception {
        byte[] png = image("png");

        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(png))
                .andExpect(status().isOk());

        byte[] stored = dominioRepository.findByCodDominio(ID_DOMINIO).orElseThrow().getLogo();
        String expected = Base64.getEncoder().encodeToString(png);
        Assertions.assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void getDecodesLegacyBase64TextColumn() throws Exception {
        // Formato storico (nessuna migrazione): la colonna contiene i byte UTF-8 del testo Base64.
        byte[] png = image("png");
        Dominio d = dominioRepository.findByCodDominio(ID_DOMINIO).orElseThrow();
        d.setLogo(Base64.getEncoder().encodeToString(png).getBytes(StandardCharsets.UTF_8));
        dominioRepository.save(d);

        byte[] body = mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        Assertions.assertThat(body).isEqualTo(png);
    }

    @Test
    void putJpegThenGetReturnsJpeg() throws Exception {
        byte[] jpeg = image("jpeg");

        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_JPEG).content(jpeg))
                .andExpect(status().isOk());

        mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_JPEG));
    }

    @Test
    void putIsIdempotentOverwrite() throws Exception {
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(image("png")))
                .andExpect(status().isOk());
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_JPEG).content(image("jpeg")))
                .andExpect(status().isOk());

        mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_JPEG));
    }

    // --- Validazione ---

    @Test
    void tooLargeReturns413() throws Exception {
        byte[] big = new byte[262144 + 1];
        Arrays.fill(big, (byte) 0x41);
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(big))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("262144")));
    }

    @Test
    void contentBytesNotSupportedReturns415() throws Exception {
        // Content-Type dichiarato accettato dal routing, ma i byte sono un GIF -> 415 dal sniffing.
        byte[] gif = ("GIF89a" + "x".repeat(32)).getBytes(StandardCharsets.US_ASCII);
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(gif))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("image/gif")));
    }

    @Test
    void unsupportedDeclaredContentTypeReturns415() throws Exception {
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(MediaType.valueOf("image/gif")).content(image("png")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType("application/problem+json"));
    }

    // --- 404 ---

    @Test
    void getWithoutLogoReturns404() throws Exception {
        mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"));
    }

    @Test
    void getUnknownDominioReturns404() throws Exception {
        mvc.perform(get("/domini/99999999999/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void putUnknownDominioReturns404() throws Exception {
        mvc.perform(put("/domini/99999999999/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(image("png")))
                .andExpect(status().isNotFound());
    }

    // --- Delete ---

    @Test
    void deleteRemovesLogoThenGetReturns404() throws Exception {
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(image("png")))
                .andExpect(status().isOk());

        mvc.perform(delete("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithoutLogoIsIdempotent204() throws Exception {
        mvc.perform(delete("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownDominioReturns404() throws Exception {
        mvc.perform(delete("/domini/99999999999/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWritesAudit() throws Exception {
        long before = countAudit(DominioLogoService.AZIONE_AUDIT_RIMUOVI);
        mvc.perform(delete("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD)))
                .andExpect(status().isNoContent());
        Assertions.assertThat(countAudit(DominioLogoService.AZIONE_AUDIT_RIMUOVI)).isEqualTo(before + 1);
    }

    // --- Sicurezza + audit ---

    @Test
    void putRequiresAuthentication() throws Exception {
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo")
                        .contentType(IMAGE_PNG).content(image("png")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void putWritesAudit() throws Exception {
        long before = countAudit(DominioLogoService.AZIONE_AUDIT_MODIFICA);
        mvc.perform(put("/domini/" + ID_DOMINIO + "/logo").with(httpBasic(PRINCIPAL, PASSWORD))
                        .contentType(IMAGE_PNG).content(image("png")))
                .andExpect(status().isOk());
        Assertions.assertThat(countAudit(DominioLogoService.AZIONE_AUDIT_MODIFICA)).isEqualTo(before + 1);
    }

    // --- helpers ---

    private static byte[] image(String format) throws IOException {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) {
            throw new IllegalStateException("Nessun writer ImageIO per il formato " + format);
        }
        return out.toByteArray();
    }

    private long countAudit(String azione) {
        return gpAuditRepository.findAll().stream()
                .filter(a -> azione.equals(a.getTipoOggetto()))
                .count();
    }
}
