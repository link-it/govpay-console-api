package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.PaymentNotice;

class AvvisoPdfPayloadMapperTest {

    private final AvvisoPdfPayloadMapper mapper = new AvvisoPdfPayloadMapper();

    private static Versamento versamento() {
        Stazione stazione = new Stazione();
        stazione.setApplicationCode(1);
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setAuxDigit(0);
        dominio.setStazione(stazione);
        Versamento v = new Versamento();
        v.setDominio(dominio);
        v.setIuvVersamento("123456789012345");
        v.setNumeroAvviso("001123456789012345");
        v.setImportoTotale(100.0);
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        return v;
    }

    @Test
    void fullQrcodeBuiltFromNumeroAvviso() {
        PaymentNotice notice = mapper.toPaymentNotice(versamento(), null);

        assertThat(notice.getFull().getQrcode())
                .isEqualTo("PAGOPA|002|001123456789012345|12345678901|10000");
    }

    @Test
    void fullQrcodeDerivedWhenNumeroAvvisoMissing() {
        Versamento v = versamento();
        v.setNumeroAvviso(null);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        // auxDigit 0: "0" + applicationCode a 2 cifre + IUV
        assertThat(notice.getFull().getQrcode())
                .isEqualTo("PAGOPA|002|001123456789012345|12345678901|10000");
    }

    @Test
    void throwsAvvisoNonDisponibileWhenIuvMissing() {
        Versamento v = versamento();
        v.setIuvVersamento(null);
        v.setNumeroAvviso(null);

        assertThatThrownBy(() -> mapper.toPaymentNotice(v, null))
                .isInstanceOf(AvvisoNonDisponibileException.class)
                .hasMessageContaining("codice QR");
    }

    @Test
    void firstLogoIsStoredBase64TextAsIs() {
        // stampe incorpora il logo come testo base64 nell'XML Jasper: si
        // inoltra il contenuto colonna tal quale, senza decodificarlo
        byte[] stored = Base64.getEncoder().encode("png-bytes".getBytes(StandardCharsets.UTF_8));
        Versamento v = versamento();
        v.getDominio().setLogo(stored);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getFirstLogo()).isEqualTo(stored);
    }

    @Test
    void firstLogoEmptyWhenDominioHasNoLogo() {
        PaymentNotice notice = mapper.toPaymentNotice(versamento(), null);

        assertThat(notice.getFirstLogo()).isEmpty();
    }

    @Test
    void deMapsToClientDE() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.DE)).isEqualTo(Languages.DE);
    }

    @Test
    void enMapsToClientEN() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.EN)).isEqualTo(Languages.EN);
    }

    @Test
    void frMapsToClientFR() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.FR)).isEqualTo(Languages.FR);
    }

    @Test
    void slMapsToClientSL() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.SL)).isEqualTo(Languages.SL);
    }

    @Test
    void noneMapsToNull() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(LinguaSecondaria.NONE)).isNull();
    }

    @Test
    void nullMapsToNull() {
        assertThat(AvvisoPdfPayloadMapper.toClientLanguage(null)).isNull();
    }
}
