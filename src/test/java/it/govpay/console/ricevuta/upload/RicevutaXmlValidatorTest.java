package it.govpay.console.ricevuta.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.govpay.console.web.UnprocessableEntityException;

/**
 * Copre il caricamento dello schema e la validazione. Il primo test e' la
 * regressione dell'avvio fallito in container: lo schema deve caricarsi anche
 * quando le risorse del classpath <b>non</b> hanno protocollo {@code file:}.
 */
class RicevutaXmlValidatorTest {

    private static final String[] XSD = {
            "xsd/pagopa/paForNode.xsd", "xsd/pagopa/sac-common-types-1.0.xsd" };

    /**
     * Con gli XSD dentro un jar le risorse hanno protocollo {@code jar:file:}, non
     * {@code file:} — la stessa condizione che nel fat jar di Spring Boot diventa
     * {@code jar:nested:} e che faceva fallire l'avvio con
     * "'nested' access is not allowed due to restriction set by the
     * accessExternalSchema property".
     * <p>
     * Non e' un test di stile: con l'implementazione precedente, che passava il
     * systemId della risorsa a {@code newSchema} e ammetteva il solo protocollo
     * {@code file}, questo test falliva.
     */
    /**
     * Riproduce l'avvio fallito in container. Nel fat jar di Spring Boot le risorse
     * hanno protocollo {@code jar:nested:}, e la JDK, controllando
     * {@code accessExternalSchema}, ne estrae il sotto-protocollo {@code nested}: non
     * essendo fra quelli ammessi, l'import di {@code sac-common-types-1.0.xsd}
     * fallisce con "'nested' access is not allowed" e il bean non si crea.
     * <p>
     * Un jar normale non basta a riprodurlo, perché da {@code jar:file:} la JDK
     * estrae {@code file} e lo ammette. Qui si usa quindi un protocollo inventato,
     * che nessuna lista di permessi contiene: la condizione è la stessa.
     * <p>
     * Con l'implementazione precedente — systemId della risorsa passato a
     * {@code newSchema} e accesso ammesso al solo {@code file} — questo test
     * fallisce. Con il precaricamento dello schema importato passa, perché nessun
     * URL viene più risolto.
     */
    @Test
    void schemaCaricabileAncheConRisorseDaProtocolloNonAmmesso() throws Exception {
        ClassLoader precedente = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(precedente) {
                @Override
                public URL getResource(String nome) {
                    if (!nome.startsWith("xsd/pagopa/")) {
                        return super.getResource(nome);
                    }
                    try {
                        return new URL("x-nested", "", -1, "/" + nome, new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return new URLConnection(u) {
                                    @Override
                                    public void connect() {
                                        // niente da fare
                                    }

                                    @Override
                                    public InputStream getInputStream() {
                                        return new ByteArrayInputStream(
                                                fixtureClasspath(u.getPath().substring(1)));
                                    }
                                };
                            }
                        });
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
            assertThatCode(RicevutaXmlValidator::new).doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(precedente);
        }
    }

    /** Lo schema si carica anche quando le risorse stanno in un jar. */
    @Test
    void schemaCaricabileConRisorseInJar(@TempDir Path tmp) throws Exception {
        URL jar = jarConGliXsd(tmp);
        ClassLoader precedente = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader soloJar = new URLClassLoader(new URL[] { jar }, null)) {
            assertThat(soloJar.getResource(XSD[0]).getProtocol()).isEqualTo("jar");
            Thread.currentThread().setContextClassLoader(new ClassLoader(precedente) {
                @Override
                public URL getResource(String nome) {
                    return nome.startsWith("xsd/pagopa/") ? soloJar.getResource(nome) : super.getResource(nome);
                }

                @Override
                public InputStream getResourceAsStream(String nome) {
                    return nome.startsWith("xsd/pagopa/")
                            ? soloJar.getResourceAsStream(nome)
                            : super.getResourceAsStream(nome);
                }
            });
            assertThatCode(RicevutaXmlValidator::new).doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(precedente);
        }
    }

    @Test
    void rtV2_2ConformePassa() throws Exception {
        new RicevutaXmlValidator().validate(fixture("rt-v2_2-ok.xml"), RicevutaFormato.V2_2);
    }

    @Test
    void rtV2ConformePassa() throws Exception {
        new RicevutaXmlValidator().validate(fixture("rt-v2-ok.xml"), RicevutaFormato.V2);
    }

    /**
     * La ragione per cui questa classe esiste: {@code ctTransferPAReceiptV2} ha una
     * {@code <xsd:choice>} fra {@code IBAN} e {@code MBDAttachment}, quindi una voce
     * priva di entrambi viola lo schema. Senza validazione JAXB la accetterebbe in
     * silenzio e il documento partirebbe verso api-pagopa.
     */
    @Test
    void transferSenzaIbanESenzaMbdAttachmentVieneRifiutato() {
        byte[] xml = fixture("rt-v2_2-ok.xml");
        String senzaIban = new String(xml, StandardCharsets.UTF_8)
                .replaceAll("(?s)<[^>]*IBAN[^>]*>.*?</[^>]*IBAN>", "");
        assertThat(senzaIban).doesNotContain("IBAN").doesNotContain("MBDAttachment");

        assertThatThrownBy(() -> new RicevutaXmlValidator()
                .validate(senzaIban.getBytes(StandardCharsets.UTF_8), RicevutaFormato.V2_2))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("non conforme");
    }

    // --- helpers ---

    private static URL jarConGliXsd(Path tmp) throws IOException {
        Path jar = tmp.resolve("xsd.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String risorsa : XSD) {
                out.putNextEntry(new JarEntry(risorsa));
                out.write(fixtureClasspath(risorsa));
                out.closeEntry();
            }
        }
        return jar.toUri().toURL();
    }

    private static byte[] fixtureClasspath(String risorsa) {
        try (InputStream in = RicevutaXmlValidatorTest.class.getClassLoader().getResourceAsStream(risorsa)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(risorsa, e);
        }
    }

    private static byte[] fixture(String nome) {
        return fixtureClasspath(nome);
    }
}
