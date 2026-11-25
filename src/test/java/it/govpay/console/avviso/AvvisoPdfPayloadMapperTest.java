package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Stazione;
import it.govpay.console.model.LinguaSecondaria;
import it.govpay.console.web.BadRequestException;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.TipoSoggetto;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.stampe.client.model.Languages;
import it.govpay.stampe.client.model.PaymentNotice;
import it.govpay.stampe.client.model.ThresholdType;
import tools.jackson.databind.json.JsonMapper;

class AvvisoPdfPayloadMapperTest {

    private final AvvisoPdfPayloadMapper mapper = new AvvisoPdfPayloadMapper(JsonMapper.shared());

    private static Dominio dominio() {
        Stazione stazione = new Stazione();
        stazione.setApplicationCode(1);
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setAuxDigit(0);
        dominio.setStazione(stazione);
        return dominio;
    }

    /** Pendenza v3 con posizione, debitore e opzione a soluzione unica. */
    private static Pendenza pendenza() {
        PosizioneDebitoria posizione = new PosizioneDebitoria();
        posizione.setDescrizione("Posizione di test");
        Debitore debitore = new Debitore();
        debitore.setTipo(TipoSoggetto.F);
        debitore.setIdentificativo("RSSMRA80A01H501U");
        debitore.setAnagrafica("Mario Rossi");
        PdDebitore pdDebitore = new PdDebitore();
        pdDebitore.setDebitore(debitore);
        posizione.addPdDebitore(pdDebitore);
        OpzionePagamento opzione = new OpzionePagamento();
        opzione.setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA);
        pdDebitore.addOpzionePagamento(opzione);
        Pendenza p = new Pendenza();
        p.setIdPendenza("PEND-1");
        p.setIuv("123456789012345");
        p.setNumeroAvviso("001123456789012345");
        p.setImporto(100.0);
        opzione.addPendenza(p);
        return p;
    }

    private static Debitore debitore(Pendenza p) {
        return p.getOpzionePagamento().getPdDebitore().getDebitore();
    }

    private static IbanAccredito iban(String codIban, boolean postale) {
        IbanAccredito i = new IbanAccredito();
        i.setCodIban(codIban);
        i.setPostale(postale);
        return i;
    }

    private PaymentNotice notice(Pendenza p, Dominio dominio, LinguaSecondaria lingua) {
        return mapper.toPaymentNotice(p, dominio, null, lingua);
    }

    @Test
    void fullQrcodeBuiltFromNumeroAvviso() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getFull().getQrcode())
                .isEqualTo("PAGOPA|002|001123456789012345|12345678901|10000");
        assertThat(notice.getReducedPayments()).isEmpty();
    }

    @Test
    void fullQrcodeDerivedWhenNumeroAvvisoMissing() {
        Pendenza p = pendenza();
        p.setNumeroAvviso(null);

        PaymentNotice notice = notice(p, dominio(), null);

        // auxDigit 0: "0" + applicationCode a 2 cifre + IUV
        assertThat(notice.getFull().getQrcode())
                .isEqualTo("PAGOPA|002|001123456789012345|12345678901|10000");
    }

    @Test
    void throwsAvvisoNonDisponibileWhenIuvMissing() {
        Pendenza p = pendenza();
        p.setIuv(null);
        p.setNumeroAvviso(null);
        Dominio dominio = dominio();

        assertThatThrownBy(() -> notice(p, dominio, null))
                .isInstanceOf(AvvisoNonDisponibileException.class)
                .hasMessageContaining("codice QR");
    }

    @Test
    void firstLogoIsStoredBase64TextAsIs() throws java.io.IOException {
        // stampe incorpora il logo come testo base64 nell'XML Jasper: si
        // inoltra il contenuto colonna tal quale, senza decodificarlo
        byte[] stored = Base64.getEncoder().encode(png());
        Dominio dominio = dominio();
        dominio.setLogo(stored);

        PaymentNotice notice = notice(pendenza(), dominio, null);

        assertThat(notice.getFirstLogo()).isEqualTo(stored);
    }

    @Test
    void firstLogoEmptyWhenStoredLogoIsNotAnImage() {
        // issue #124: valore salvato dalle vecchie PUT dell'API Backoffice
        // (il link della GET): si invia contenuto vuoto, govpay-stampe usa il
        // logo di default
        Dominio dominio = dominio();
        dominio.setLogo("/domini/12345678901/logo".getBytes(StandardCharsets.UTF_8));

        PaymentNotice notice = notice(pendenza(), dominio, null);

        assertThat(notice.getFirstLogo()).isEmpty();
    }

    private static byte[] png() throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void firstLogoEmptyWhenDominioHasNoLogo() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getFirstLogo()).isEmpty();
    }

    // --- bollettino postale: l'IBAN scelto (vedi IbanPostaleResolverTest) finisce nel payload ---

    @Test
    void ibanPostaleAttivaIlBollettinoEValorizzaLIban() {
        PaymentNotice notice = mapper.toPaymentNotice(pendenza(), dominio(),
                iban("IT60X0542811101000000123456", true), null);

        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getFull().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000123456");
    }

    @Test
    void nessunIbanPostaleNessunBollettinoPostale() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getPostal()).isFalse();
        assertThat(notice.getFull().getIban()).isNull();
    }

    /**
     * Intestatario e autorizzazione si inviano grezzi: i fallback (ente creditore,
     * autorizzazione del dominio) li applica govpay-stampe.
     */
    @Test
    void intestatarioEAutorizzazioneInoltratiGrezzi() {
        Dominio dominio = dominio();
        dominio.setAutStampaPoste("Aut. Ente n. 1 del 01/01/2020");
        IbanAccredito postale = iban("IT60X0542811101000000123456", true);
        postale.setIntestatario("Comune di Test");
        postale.setAutStampaPoste("Aut. Iban n. 2 del 02/02/2020");

        PaymentNotice notice = mapper.toPaymentNotice(pendenza(), dominio, postale, null);

        assertThat(notice.getFull().getIban().getOwnerBusinessName()).isEqualTo("Comune di Test");
        assertThat(notice.getFull().getIban().getPostalAuthMessage()).isEqualTo("Aut. Iban n. 2 del 02/02/2020");
        assertThat(notice.getCreditor().getPostalAuthMessage()).isEqualTo("Aut. Ente n. 1 del 01/01/2020");
    }

    @Test
    void cbillCodeLettoDalDominio() {
        Dominio dominio = dominio();
        dominio.setCbill("ABCDE");

        PaymentNotice notice = notice(pendenza(), dominio, null);

        assertThat(notice.getCreditor().getCbillCode()).isEqualTo("ABCDE");
    }

    @Test
    void ilCreditoreRiportaIlPagamentoSulSitoDelDominio() {
        Dominio dominio = dominio();
        dominio.setPagamentoSito(Boolean.TRUE);

        PaymentNotice notice = notice(pendenza(), dominio, null);

        assertThat(notice.getCreditor().getPayOnWebsite()).isTrue();
    }

    @Test
    void senzaPagamentoSitoIlCreditoreNonLoRiporta() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getCreditor().getPayOnWebsite()).isFalse();
    }

    @Test
    void cbillCodeAssenteSeDominioNonLoHaConfigurato() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getCreditor().getCbillCode()).isNull();
    }

    /**
     * Le colonne a DB sono varchar(255), il contratto stampe valida maxLength 50/70:
     * si tronca, perche' un campo di sola resa grafica non deve far fallire il PDF.
     */
    @Test
    void intestatarioEAutorizzazioneTroncatiAiLimitiDelContratto() {
        IbanAccredito postale = iban("IT60X0542811101000000123456", true);
        postale.setIntestatario("X".repeat(60));
        postale.setAutStampaPoste("Y".repeat(80));

        PaymentNotice notice = mapper.toPaymentNotice(pendenza(), dominio(), postale, null);

        assertThat(notice.getFull().getIban().getOwnerBusinessName()).hasSize(50);
        assertThat(notice.getFull().getIban().getPostalAuthMessage()).hasSize(70);
    }

    @Test
    void businessNameTroncatoA50Caratteri() {
        Dominio dominio = dominio();
        dominio.setRagioneSociale("A".repeat(60));

        PaymentNotice notice = notice(pendenza(), dominio, null);

        assertThat(notice.getCreditor().getBusinessName()).hasSize(50);
    }

    @Test
    void fullNameTroncatoA70Caratteri() {
        Pendenza p = pendenza();
        debitore(p).setAnagrafica("B".repeat(80));

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getDebtor().getFullName()).hasSize(70);
    }

    @Test
    void ildebitoreEQuelloDellOpzioneDellaPendenza() {
        Pendenza p = pendenza();
        debitore(p).setIdentificativo("VRDLGI90B02H501T");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getDebtor().getFiscalCode()).isEqualTo("VRDLGI90B02H501T");
    }

    /**
     * {@code indirizzo varchar(70)} + {@code civico varchar(16)} possono insieme superare 70:
     * il limite dello schema (pagoPA) si applica al campo risultante, non alle singole
     * colonne concatenate.
     */
    @Test
    void addressLine1TroncatoA70CaratteriDopoLaConcatenazione() {
        Pendenza p = pendenza();
        debitore(p).setIndirizzo("C".repeat(70));
        debitore(p).setCivico("1234567890123456");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getDebtor().getAddressLine1()).hasSize(70);
    }

    @Test
    void addressLine2TroncatoA70CaratteriDopoLaConcatenazione() {
        Pendenza p = pendenza();
        debitore(p).setCap("0000000000000000");
        debitore(p).setLocalita("D".repeat(35));
        debitore(p).setProvincia("E".repeat(35));

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getDebtor().getAddressLine2()).hasSize(70);
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

    // --- title ('oggetto del pagamento'): la causale vera, non un'intestazione fissa ---

    @Test
    void titleEhLaCausaleDecodificata() {
        Pendenza p = pendenza();
        p.setCausaleVersamento("01 " + Base64.getEncoder().encodeToString(
                "Pagamento TARI 2026".getBytes(StandardCharsets.UTF_8)));

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getTitle()).isEqualTo("Pagamento TARI 2026");
    }

    @Test
    void titleEhLaDescrizioneDellaPosizioneSeLaCausaleEAssente() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getTitle()).isEqualTo("Posizione di test");
    }

    // --- scadenza ---

    @Test
    void dueDateEQuellaDellaPendenza() {
        Pendenza p = pendenza();
        p.setDataScadenzaAvviso(OffsetDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZoneOffset.UTC));
        p.getOpzionePagamento().setDataScadenza(LocalDate.of(2026, 12, 31));

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getFull().getDueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void dueDateRipiegaSullaScadenzaDellOpzione() {
        Pendenza p = pendenza();
        p.getOpzionePagamento().setDataScadenza(LocalDate.of(2026, 12, 31));

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getFull().getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void dueDateAssenteSeNessunaScadenza() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getFull().getDueDate()).isNull();
    }

    // --- opzioni con soglia temporale ---

    @Test
    void opzioneEntroProduceUnPagamentoConSogliaEntro() {
        Pendenza p = pendenza();
        p.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO);
        p.getOpzionePagamento().setGiorni(5);

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getFull()).isNull();
        assertThat(notice.getReducedPayments()).hasSize(1);
        assertThat(notice.getReducedPayments().get(0).getThresholdType()).isEqualTo(ThresholdType.ENTRO);
        assertThat(notice.getReducedPayments().get(0).getThresholdDays()).isEqualTo(5);
        assertThat(notice.getReducedPayments().get(0).getNoticeNumber()).isEqualTo("001123456789012345");
    }

    @Test
    void opzioneOltreProduceUnPagamentoConSogliaOltre() {
        Pendenza p = pendenza();
        p.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE);
        p.getOpzionePagamento().setGiorni(30);

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getReducedPayments().get(0).getThresholdType()).isEqualTo(ThresholdType.OLTRE);
        assertThat(notice.getReducedPayments().get(0).getThresholdDays()).isEqualTo(30);
    }

    @Test
    void unaRataDiUnPianoRatealeStampaSoloLaSuaRata() {
        Pendenza p = pendenza();
        p.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.PIANO_RATEALE);
        p.setNumeroRata(2);

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getFull().getNoticeNumber()).isEqualTo("001123456789012345");
        assertThat(notice.getInstalments()).isEmpty();
    }

    // --- controlli preliminari ---

    @Test
    void anagrafcaDebitoreAssente() {
        Pendenza p = pendenza();
        assertThat(AvvisoPdfPayloadMapper.isAnagraficaDebitoreAssente(p)).isFalse();

        debitore(p).setAnagrafica(" ");

        assertThat(AvvisoPdfPayloadMapper.isAnagraficaDebitoreAssente(p)).isTrue();
    }

    @Test
    void marcaDaBolloSeUnaVoceHaTipoHashEProvincia() {
        Pendenza p = pendenza();
        assertThat(AvvisoPdfPayloadMapper.isPendenzaMbt(p)).isFalse();

        VocePendenza voce = new VocePendenza();
        voce.setTipoBollo("01");
        voce.setHashDocumento("aGFzaA==");
        p.addVocePendenza(voce);
        assertThat(AvvisoPdfPayloadMapper.isPendenzaMbt(p)).isFalse();

        voce.setProvinciaResidenza("RM");
        assertThat(AvvisoPdfPayloadMapper.isPendenzaMbt(p)).isTrue();
    }

    // --- bilinguismo e informativa: letti da Pendenza.proprieta (JSON) ---

    @Test
    void secondLanguageRifiutataSenzaCausaleTradottaInProprieta() {
        Pendenza p = pendenza();
        Dominio dominio = dominio();

        assertThatThrownBy(() -> notice(p, dominio, LinguaSecondaria.EN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("causale tradotta");
    }

    @Test
    void secondLanguageTitleLettaDaProprieta() {
        Pendenza p = pendenza();
        p.setProprieta("{\"linguaSecondariaCausale\": \"TARI payment 2026\"}");

        PaymentNotice notice = notice(p, dominio(), LinguaSecondaria.EN);

        assertThat(notice.getSecondLanguage().getTitle()).isEqualTo("TARI payment 2026");
    }

    @Test
    void secondLanguageInformativaImportoAssenteSenzaProprieta() {
        Pendenza p = pendenza();
        p.setProprieta("{\"linguaSecondariaCausale\": \"TARI payment 2026\"}");

        PaymentNotice notice = notice(p, dominio(), LinguaSecondaria.EN);

        assertThat(notice.getSecondLanguage().getInformativaImporto()).isNull();
    }

    @Test
    void secondLanguageInformativaImportoLettaDaProprieta() {
        Pendenza p = pendenza();
        p.setProprieta("{\"linguaSecondariaCausale\": \"TARI payment 2026\", "
                + "\"linguaSecondariaInformativaImportoAvviso\": \"Custom text\"}");

        PaymentNotice notice = notice(p, dominio(), LinguaSecondaria.EN);

        assertThat(notice.getSecondLanguage().getInformativaImporto()).isEqualTo("Custom text");
    }

    @Test
    void informativaImportoAssenteSenzaProprieta() {
        PaymentNotice notice = notice(pendenza(), dominio(), null);

        assertThat(notice.getInformativaImporto()).isNull();
    }

    @Test
    void informativaImportoLettaDaProprieta() {
        Pendenza p = pendenza();
        p.setProprieta("{\"informativaImportoAvviso\": \"Testo personalizzato\"}");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getInformativaImporto()).isEqualTo("Testo personalizzato");
    }

    @Test
    void informativaImportoVuotaOmetteLaNota() {
        Pendenza p = pendenza();
        p.setProprieta("{\"informativaImportoAvviso\": \"\"}");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getInformativaImporto()).isEmpty();
    }

    /** Campi non modellati (descrizioneImporto, ecc.) non devono far fallire il parsing. */
    @Test
    void informativaImportoIgnoraAltriCampiDiProprieta() {
        Pendenza p = pendenza();
        p.setProprieta("{\"linguaSecondaria\": \"eng\", \"informativaImportoAvviso\": \"Nota custom\", "
                + "\"descrizioneImporto\": [{\"voce\": \"x\"}]}");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getInformativaImporto()).isEqualTo("Nota custom");
    }

    @Test
    void informativaImportoAssenteConProprietaMalformata() {
        Pendenza p = pendenza();
        p.setProprieta("{non e' json valido");

        PaymentNotice notice = notice(p, dominio(), null);

        assertThat(notice.getInformativaImporto()).isNull();
    }
}
