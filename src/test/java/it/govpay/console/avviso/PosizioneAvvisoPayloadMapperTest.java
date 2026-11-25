package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Stazione;
import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.model.TipoSoggetto;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import it.govpay.stampe.client.model.CdsViolation;
import it.govpay.stampe.client.model.PaymentNotice;
import it.govpay.stampe.client.model.ThresholdType;
import tools.jackson.databind.json.JsonMapper;

/** Avvisi complessivi della posizione: un test per layout. */
class PosizioneAvvisoPayloadMapperTest {

    private final AvvisoPdfPayloadMapper mapper = new AvvisoPdfPayloadMapper(JsonMapper.shared());

    private final Dominio dominio = dominio();
    private final Map<Pendenza, IbanAccredito> postali = new HashMap<>();
    private final AvvisoPdfPayloadMapper.Risolutore risolutore = new AvvisoPdfPayloadMapper.Risolutore() {
        @Override
        public Dominio dominio(Pendenza pendenza) {
            return dominio;
        }

        @Override
        public IbanAccredito iban(Pendenza pendenza) {
            return postali.get(pendenza);
        }
    };

    private final PdDebitore pdDebitore = pdDebitore();
    private int contatore;

    private static Dominio dominio() {
        Stazione stazione = new Stazione();
        stazione.setApplicationCode(1);
        Dominio d = new Dominio();
        d.setCodDominio("12345678901");
        d.setRagioneSociale("Comune di Test");
        d.setAuxDigit(0);
        d.setStazione(stazione);
        return d;
    }

    private static PdDebitore pdDebitore() {
        PosizioneDebitoria posizione = new PosizioneDebitoria();
        posizione.setDescrizione("Posizione di test");
        Debitore debitore = new Debitore();
        debitore.setTipo(TipoSoggetto.F);
        debitore.setIdentificativo("RSSMRA80A01H501U");
        debitore.setAnagrafica("Mario Rossi");
        PdDebitore pd = new PdDebitore();
        pd.setDebitore(debitore);
        posizione.addPdDebitore(pd);
        return pd;
    }

    /** Aggiunge un'opzione del tipo dato con {@code rate} pendenze, e le restituisce. */
    private List<Pendenza> opzione(TipologiaOpzionePagamento tipologia, Integer giorni, int rate) {
        OpzionePagamento opzione = new OpzionePagamento();
        opzione.setTipologia(tipologia);
        opzione.setGiorni(giorni);
        pdDebitore.addOpzionePagamento(opzione);
        List<Pendenza> pendenze = new java.util.ArrayList<>();
        for (int i = 1; i <= rate; i++) {
            contatore++;
            Pendenza p = new Pendenza();
            p.setIdPendenza("PEND-" + contatore);
            p.setIuv("12345678901" + String.format("%04d", contatore));
            p.setNumeroAvviso("00112345678901" + String.format("%04d", contatore));
            p.setImporto(100.0 * contatore);
            p.setNumeroRata(i);
            opzione.addPendenza(p);
            pendenze.add(p);
        }
        return pendenze;
    }

    private static IbanAccredito postale(String codIban) {
        IbanAccredito i = new IbanAccredito();
        i.setCodIban(codIban);
        i.setPostale(true);
        return i;
    }

    // --- una sola opzione ---

    @Test
    void unaPendenzaSolaUsaLaDescrizioneDellaPosizioneComeTitolo() {
        Pendenza p = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, 1).get(0);
        p.setCausaleVersamento("causale della pendenza");

        PaymentNotice notice = mapper.toPaymentNoticeDocumento(p, risolutore);

        assertThat(notice.getTitle()).isEqualTo("Posizione di test");
        assertThat(notice.getFull().getNoticeNumber()).isEqualTo(p.getNumeroAvviso());
        assertThat(notice.getInstalments()).isEmpty();
        assertThat(notice.getOptions()).isEmpty();
    }

    @Test
    void ilPianoRatealeProduceUnaInstalmentPerRata() {
        List<Pendenza> rate = opzione(TipologiaOpzionePagamento.PIANO_RATEALE, null, 3);

        PaymentNotice notice = mapper.toPaymentNoticeRate(rate, risolutore);

        assertThat(notice.getFull()).isNull();
        assertThat(notice.getInstalments()).hasSize(3);
        assertThat(notice.getInstalments()).extracting(i -> i.getInstalmentNumber()).containsExactly(1, 2, 3);
        assertThat(notice.getInstalments().get(1).getNoticeNumber()).isEqualTo(rate.get(1).getNumeroAvviso());
        assertThat(notice.getInstalments().get(1).getAmount()).isEqualTo(rate.get(1).getImporto());
        assertThat(notice.getTitle()).isEqualTo("Posizione di test");
        assertThat(notice.getDebtor().getFiscalCode()).isEqualTo("RSSMRA80A01H501U");
    }

    @Test
    void ilPianoRatealeEPostaleSoloSeTutteLeRateHannoUnIbanPostale() {
        List<Pendenza> rate = opzione(TipologiaOpzionePagamento.PIANO_RATEALE, null, 2);
        postali.put(rate.get(0), postale("IT60X0542811101000000123456"));

        assertThat(mapper.toPaymentNoticeRate(rate, risolutore).getPostal()).isFalse();

        postali.put(rate.get(1), postale("IT60X0542811101000000123456"));

        PaymentNotice notice = mapper.toPaymentNoticeRate(rate, risolutore);
        assertThat(notice.getPostal()).isTrue();
        assertThat(notice.getInstalments().get(0).getIban().getIbanCode()).isEqualTo("IT60X0542811101000000123456");
    }

    // --- piu' opzioni ---

    @Test
    void piuOpzioniAlternativeProduconoUnElementoOptionsPerOpzione() {
        List<List<Pendenza>> opzioni = List.of(
                opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, 1),
                opzione(TipologiaOpzionePagamento.PIANO_RATEALE, null, 3));

        PaymentNotice notice = mapper.toPaymentNoticeOpzioni(opzioni, risolutore);

        assertThat(notice.getOptions()).hasSize(2);
        assertThat(notice.getOptions().get(0).getFull().getNoticeNumber())
                .isEqualTo(opzioni.get(0).get(0).getNumeroAvviso());
        assertThat(notice.getOptions().get(0).getInstalments()).isEmpty();
        assertThat(notice.getOptions().get(1).getFull()).isNull();
        assertThat(notice.getOptions().get(1).getInstalments()).hasSize(3);
        assertThat(notice.getFull()).isNull();
    }

    @Test
    void ilCreditoreRiportaIlPagamentoSulSitoDelDominio() {
        dominio.setPagamentoSito(Boolean.TRUE);
        Pendenza p = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, 1).get(0);

        assertThat(mapper.toPaymentNoticeDocumento(p, risolutore).getCreditor().getPayOnWebsite()).isTrue();
    }

    @Test
    void senzaPagamentoSitoIlCreditoreNonLoRiporta() {
        dominio.setPagamentoSito(Boolean.FALSE);
        Pendenza p = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, 1).get(0);

        assertThat(mapper.toPaymentNoticeDocumento(p, risolutore).getCreditor().getPayOnWebsite()).isFalse();
    }

    // --- soglie temporali ---

    @Test
    void entroEOltreSuUnaPaginaOrdinatiPerGiorni() {
        List<Pendenza> oltre = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE, 30, 1);
        List<Pendenza> entro = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, 5, 1);
        List<List<Pendenza>> opzioni = List.of(oltre, entro);

        assertThat(AvvisoPdfPayloadMapper.sonoTutteSoglie(opzioni)).isTrue();
        PaymentNotice notice = mapper.toPaymentNoticeSoglie(List.of(oltre.get(0), entro.get(0)), risolutore);

        assertThat(notice.getFull()).isNull();
        assertThat(notice.getReducedPayments()).hasSize(2);
        assertThat(notice.getReducedPayments().get(0).getThresholdType()).isEqualTo(ThresholdType.ENTRO);
        assertThat(notice.getReducedPayments().get(0).getThresholdDays()).isEqualTo(5);
        assertThat(notice.getReducedPayments().get(1).getThresholdType()).isEqualTo(ThresholdType.OLTRE);
        assertThat(notice.getReducedPayments().get(1).getThresholdDays()).isEqualTo(30);
    }

    @Test
    void aParitaDiGiorniEntroPrecedeOltre() {
        List<Pendenza> oltre = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_OLTRE, 10, 1);
        List<Pendenza> entro = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, 10, 1);

        PaymentNotice notice = mapper.toPaymentNoticeSoglie(List.of(oltre.get(0), entro.get(0)), risolutore);

        assertThat(notice.getReducedPayments().get(0).getThresholdType()).isEqualTo(ThresholdType.ENTRO);
    }

    @Test
    void laScadenzaDellOpzioneEUsataSeLaPendenzaNonLaHa() {
        Pendenza p = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO, 5, 1).get(0);
        p.getOpzionePagamento().setDataScadenza(LocalDate.of(2026, 12, 31));

        PaymentNotice notice = mapper.toPaymentNoticeSoglie(List.of(p), risolutore);

        assertThat(notice.getReducedPayments().get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    // --- Codice della Strada ---

    @Test
    void ridottoEScontatoProduconoLaViolazioneConIDueImporti() {
        List<Pendenza> scontato = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO, null, 1);
        List<Pendenza> ridotto = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO, null, 1);
        List<List<Pendenza>> opzioni = List.of(scontato, ridotto);

        assertThat(AvvisoPdfPayloadMapper.sonoRidottoEScontato(opzioni)).isTrue();
        assertThat(AvvisoPdfPayloadMapper.sonoTutteSoglie(opzioni)).isFalse();
        CdsViolation violazione = mapper.toCdsViolation(opzioni, risolutore);

        assertThat(violazione.getReducedAmount().getNoticeNumber()).isEqualTo(ridotto.get(0).getNumeroAvviso());
        assertThat(violazione.getDiscountedAmount().getNoticeNumber()).isEqualTo(scontato.get(0).getNumeroAvviso());
        assertThat(violazione.getTitle()).isEqualTo("Posizione di test");
        assertThat(violazione.getPostal()).isFalse();
    }

    @Test
    void laViolazioneEPostaleSoloSeLoSonoEntrambe() {
        List<Pendenza> ridotto = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO, null, 1);
        List<Pendenza> scontato = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO, null, 1);
        postali.put(ridotto.get(0), postale("IT60X0542811101000000123456"));

        assertThat(mapper.toCdsViolation(List.of(ridotto, scontato), risolutore).getPostal()).isFalse();

        postali.put(scontato.get(0), postale("IT60X0542811101000000654321"));

        CdsViolation violazione = mapper.toCdsViolation(List.of(ridotto, scontato), risolutore);
        assertThat(violazione.getPostal()).isTrue();
        assertThat(violazione.getReducedAmount().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000123456");
        assertThat(violazione.getDiscountedAmount().getIban().getIbanCode()).isEqualTo("IT60X0542811101000000654321");
    }

    @Test
    void lePredicatiDiLayoutRichiedonoEsattamenteDueOpzioniDiUnaPendenza() {
        List<Pendenza> ridotto = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO, null, 1);
        List<Pendenza> unica = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, 1);
        List<Pendenza> scontatoARate = opzione(TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO, null, 2);

        assertThat(AvvisoPdfPayloadMapper.sonoRidottoEScontato(List.of(ridotto, unica))).isFalse();
        assertThat(AvvisoPdfPayloadMapper.sonoRidottoEScontato(List.of(ridotto, scontatoARate))).isFalse();
        assertThat(AvvisoPdfPayloadMapper.sonoRidottoEScontato(List.of(ridotto))).isFalse();
    }
}
