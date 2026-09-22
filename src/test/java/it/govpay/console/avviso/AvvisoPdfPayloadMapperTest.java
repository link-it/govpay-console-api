package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.Tributo;
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

    private static IbanAccredito iban(String codIban, boolean postale) {
        IbanAccredito i = new IbanAccredito();
        i.setCodIban(codIban);
        i.setPostale(postale);
        return i;
    }

    /** Aggiunge a {@code v} il primo (e unico) singolo versamento, e lo restituisce. */
    private static SingoloVersamento primoSingoloVersamento(Versamento v) {
        SingoloVersamento sv = new SingoloVersamento();
        sv.setVersamento(v);
        v.getSingoliVersamenti().add(sv);
        return sv;
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

    // --- bollettino postale: valorizzazione dell'IBAN (govpay-stampe lo esige con postal=true) ---

    @Test
    void pendenzaDefinitaPrendeLIbanPostaleDalSingoloVersamento() {
        Versamento v = versamento();
        primoSingoloVersamento(v).setIbanAccredito(iban("IT60X0542811101000000123456", true));

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getFull().getIban()).isNotNull();
        assertThat(notice.getFull().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000123456");
    }

    /**
     * Pendenza a riferimento: la FK sul singolo versamento e' nulla e l'IBAN va
     * ereditato dal tipo entrata del dominio ({@code tributi}), come fa V1 in
     * {@code SingoloVersamento.getIbanAccredito}.
     */
    @Test
    void pendenzaARiferimentoEreditaLIbanPostaleDalTipoEntrataDominio() {
        Versamento v = versamento();
        Tributo tributo = new Tributo();
        tributo.setIbanAccredito(iban("IT60X0542811101000000999999", true));
        primoSingoloVersamento(v).setTributo(tributo);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getFull().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000999999");
    }

    @Test
    void ibanSulSingoloVersamentoPrevaleSuQuelloDelTipoEntrataDominio() {
        Versamento v = versamento();
        Tributo tributo = new Tributo();
        tributo.setIbanAccredito(iban("IT60X0542811101000000999999", true));
        SingoloVersamento sv = primoSingoloVersamento(v);
        sv.setIbanAccredito(iban("IT60X0542811101000000123456", true));
        sv.setTributo(tributo);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getFull().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000123456");
    }

    /** V1 (AvvisoPagamentoV2Utils:441-446): l'appoggio si guarda solo se l'accredito non e' postale. */
    @Test
    void ibanDiAppoggioUsatoQuandoLAccreditoNonEPostale() {
        Versamento v = versamento();
        SingoloVersamento sv = primoSingoloVersamento(v);
        sv.setIbanAccredito(iban("IT60X0542811101000000123456", false));
        sv.setIbanAppoggio(iban("IT60X0542811101000000777777", true));

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getFull().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000777777");
    }

    @Test
    void nessunIbanPostaleNessunBollettinoPostale() {
        Versamento v = versamento();
        primoSingoloVersamento(v).setIbanAccredito(iban("IT60X0542811101000000123456", false));

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getPostal()).isFalse();
        assertThat(notice.getFull().getIban()).isNull();
    }

    @Test
    void pendenzaSenzaSingoliVersamentiNonEPostale() {
        PaymentNotice notice = mapper.toPaymentNotice(versamento(), null);

        assertThat(notice.getPostal()).isFalse();
        assertThat(notice.getFull().getIban()).isNull();
    }

    /**
     * Intestatario e autorizzazione si inviano grezzi: i fallback (ente creditore,
     * autorizzazione del dominio) li applica govpay-stampe.
     */
    @Test
    void intestatarioEAutorizzazioneInoltratiGrezzi() {
        Versamento v = versamento();
        v.getDominio().setAutStampaPoste("Aut. Ente n. 1 del 01/01/2020");
        IbanAccredito postale = iban("IT60X0542811101000000123456", true);
        postale.setIntestatario("Comune di Test");
        postale.setAutStampaPoste("Aut. Iban n. 2 del 02/02/2020");
        primoSingoloVersamento(v).setIbanAccredito(postale);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getFull().getIban().getOwnerBusinessName()).isEqualTo("Comune di Test");
        assertThat(notice.getFull().getIban().getPostalAuthMessage()).isEqualTo("Aut. Iban n. 2 del 02/02/2020");
        assertThat(notice.getCreditor().getPostalAuthMessage()).isEqualTo("Aut. Ente n. 1 del 01/01/2020");
    }

    /**
     * Le colonne a DB sono varchar(255), il contratto stampe valida maxLength 50/70:
     * si tronca, perche' un campo di sola resa grafica non deve far fallire il PDF.
     */
    @Test
    void intestatarioEAutorizzazioneTroncatiAiLimitiDelContratto() {
        Versamento v = versamento();
        IbanAccredito postale = iban("IT60X0542811101000000123456", true);
        postale.setIntestatario("X".repeat(60));
        postale.setAutStampaPoste("Y".repeat(80));
        primoSingoloVersamento(v).setIbanAccredito(postale);

        PaymentNotice notice = mapper.toPaymentNotice(v, null);

        assertThat(notice.getFull().getIban().getOwnerBusinessName()).hasSize(50);
        assertThat(notice.getFull().getIban().getPostalAuthMessage()).hasSize(70);
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
