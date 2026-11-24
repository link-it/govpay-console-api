package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

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
import it.govpay.stampe.client.model.PaymentNotice;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract-check del payload avviso contro lo schema OpenAPI di govpay-stampe
 * (lo stesso YAML da cui e' generato il client): ogni campo {@code required}
 * dello schema deve risultare valorizzato nel JSON prodotto dal mapper.
 * Il client generato non applica bean validation lato chiamante, quindi una
 * violazione emergerebbe solo a runtime come 400 del microservizio: questo
 * test la intercetta in build.
 *
 * <p>Nota: si verificano i required che esistono anche tra le properties
 * dello schema risolto (lo spec dichiara required orfani, es. {@code pages}).
 */
class StampePayloadContractTest {

    private static final String SPEC = "/openapi/govpay-stampe.yaml";

    private final AvvisoPdfPayloadMapper mapper = new AvvisoPdfPayloadMapper(JsonMapper.shared());
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    @SuppressWarnings("unchecked")
    void paymentNoticeSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Map<String, Object> payload = json.convertValue(mapper.toPaymentNotice(sample(), dominio(), null, null), Map.class);

        assertRequired(payload, resolve(schemas, "PaymentNotice"), "PaymentNotice");
        assertRequired((Map<String, Object>) payload.get("full"), resolve(schemas, "Amount"), "Amount");
        assertRequired((Map<String, Object>) payload.get("creditor"), resolve(schemas, "Creditor"), "Creditor");
        assertRequired((Map<String, Object>) payload.get("debtor"), resolve(schemas, "Debtor"), "Debtor");
    }

    /**
     * Il vincolo "se {@code postal} allora {@code full.iban}" non e' esprimibile
     * fra i {@code required} dello schema — {@code govpay-stampe} lo applica a
     * runtime ({@code SemanticValidator}, 422 "Iban obbligatorio in caso di
     * avviso postale"). Va quindi verificato a parte: e' proprio la regola che
     * il contract-check sui soli {@code required} non copriva.
     */
    @Test
    @SuppressWarnings("unchecked")
    void avvisoPostaleInviaLIbanRichiestoDaStampe() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Map<String, Object> payload = json.convertValue(mapper.toPaymentNotice(sample(), dominio(), postale(), null), Map.class);

        assertThat(payload.get("postal")).as("il sample e' un avviso postale").isEqualTo(Boolean.TRUE);
        Map<String, Object> full = (Map<String, Object>) payload.get("full");
        assertThat(full.get("iban"))
                .as("con postal=true govpay-stampe esige full.iban, altrimenti risponde 422")
                .isNotNull();
        assertRequired((Map<String, Object>) full.get("iban"), resolve(schemas, "Iban"), "Iban");
    }

    /**
     * Con una soglia temporale l'avviso usa {@code reduced_payments}: ogni elemento deve
     * soddisfare i required di {@code ThresholdPayment}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void pagamentoConSogliaSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Pendenza p = sample();
        p.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_ENTRO);
        p.getOpzionePagamento().setGiorni(5);

        Map<String, Object> payload = json.convertValue(mapper.toPaymentNotice(p, dominio(), null, null), Map.class);

        List<Map<String, Object>> ridotti = (List<Map<String, Object>>) payload.get("reduced_payments");
        assertThat(ridotti).hasSize(1);
        assertRequired(ridotti.get(0), resolve(schemas, "ThresholdPayment"), "ThresholdPayment");
    }

    private static final AvvisoPdfPayloadMapper.Risolutore RISOLUTORE = new AvvisoPdfPayloadMapper.Risolutore() {
        @Override
        public Dominio dominio(Pendenza pendenza) {
            return StampePayloadContractTest.dominio();
        }

        @Override
        public IbanAccredito iban(Pendenza pendenza) {
            return null;
        }
    };

    /** Aggiunge alla posizione di {@code base} un'opzione del tipo dato con una pendenza, e la restituisce. */
    private static Pendenza altraOpzione(Pendenza base, TipologiaOpzionePagamento tipologia, int rata, String nav) {
        OpzionePagamento opzione = new OpzionePagamento();
        opzione.setTipologia(tipologia);
        base.getOpzionePagamento().getPdDebitore().addOpzionePagamento(opzione);
        Pendenza p = new Pendenza();
        p.setIdPendenza("PEND-" + nav);
        p.setIuv(nav.substring(1));
        p.setNumeroAvviso(nav);
        p.setImporto(50.0);
        p.setNumeroRata(rata);
        opzione.addPendenza(p);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void avvisoARateSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Pendenza prima = sample();
        Pendenza seconda = new Pendenza();
        seconda.setIdPendenza("PEND-2");
        seconda.setIuv("123456789012346");
        seconda.setNumeroAvviso("001123456789012346");
        seconda.setImporto(50.0);
        seconda.setNumeroRata(2);
        prima.getOpzionePagamento().addPendenza(seconda);

        Map<String, Object> payload = json.convertValue(
                mapper.toPaymentNoticeRate(List.of(prima, seconda), RISOLUTORE), Map.class);

        assertRequired(payload, resolve(schemas, "PaymentNotice"), "PaymentNotice");
        List<Map<String, Object>> rate = (List<Map<String, Object>>) payload.get("instalments");
        assertThat(rate).hasSize(2);
        for (Map<String, Object> rata : rate) {
            assertRequired(rata, resolve(schemas, "Instalment"), "Instalment");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void avvisoConOpzioniSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Pendenza prima = sample();
        Pendenza seconda = altraOpzione(prima, TipologiaOpzionePagamento.PIANO_RATEALE, 1, "001123456789012347");
        Pendenza terza = altraOpzione(prima, TipologiaOpzionePagamento.PIANO_RATEALE, 2, "001123456789012348");
        seconda.getOpzionePagamento().addPendenza(terza);

        Map<String, Object> payload = json.convertValue(mapper.toPaymentNoticeOpzioni(
                List.of(List.of(prima), List.of(seconda, terza)), RISOLUTORE), Map.class);

        assertRequired(payload, resolve(schemas, "PaymentNotice"), "PaymentNotice");
        List<Map<String, Object>> opzioni = (List<Map<String, Object>>) payload.get("options");
        assertThat(opzioni).hasSize(2);
        assertRequired((Map<String, Object>) opzioni.get(0).get("full"), resolve(schemas, "Amount"), "Amount");
        assertRequired(((List<Map<String, Object>>) opzioni.get(1).get("instalments")).get(0),
                resolve(schemas, "Instalment"), "Instalment");
    }

    @Test
    @SuppressWarnings("unchecked")
    void violazioneCdsSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Pendenza ridotto = sample();
        ridotto.getOpzionePagamento().setTipologia(TipologiaOpzionePagamento.SOLUZIONE_UNICA_RIDOTTO);
        Pendenza scontato = altraOpzione(ridotto, TipologiaOpzionePagamento.SOLUZIONE_UNICA_SCONTATO, 1,
                "001123456789012349");

        Map<String, Object> payload = json.convertValue(
                mapper.toCdsViolation(List.of(List.of(ridotto), List.of(scontato)), RISOLUTORE), Map.class);

        assertRequired(payload, resolve(schemas, "CdsViolation"), "CdsViolation");
        assertRequired((Map<String, Object>) payload.get("reduced_amount"), resolve(schemas, "Amount"), "Amount");
        assertRequired((Map<String, Object>) payload.get("discounted_amount"), resolve(schemas, "Amount"), "Amount");
    }

    private static IbanAccredito postale() {
        IbanAccredito postale = new IbanAccredito();
        postale.setCodIban("IT60X0542811101000000123456");
        postale.setPostale(true);
        postale.setIntestatario("Comune di Test");
        return postale;
    }

    private static Dominio dominio() {
        Stazione stazione = new Stazione();
        stazione.setApplicationCode(1);
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune di Test");
        dominio.setAuxDigit(0);
        dominio.setStazione(stazione);
        dominio.setLogo(Base64.getEncoder().encode("png".getBytes(StandardCharsets.UTF_8)));
        return dominio;
    }

    private static Pendenza sample() {
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
        p.setDataScadenzaAvviso(OffsetDateTime.now().plusDays(30));
        p.setCausaleVersamento("01 "
                + Base64.getEncoder().encodeToString("Pagamento di test".getBytes(StandardCharsets.UTF_8)));
        opzione.addPendenza(p);
        return p;
    }

    private void assertRequired(Map<String, Object> node, Schema schema, String context) {
        assertThat(node).as(context + ": nodo presente nel payload").isNotNull();
        for (String field : schema.required) {
            if (!schema.properties.containsKey(field)) {
                continue; // required orfano nello spec (es. NoticeMetadata.pages)
            }
            assertThat(node.get(field))
                    .as(context + "." + field + " e' required per govpay-stampe")
                    .isNotNull();
        }
    }

    /** Schema con allOf/$ref risolti (un livello, sufficiente per questo spec). */
    @SuppressWarnings("unchecked")
    private Schema resolve(Map<String, Object> schemas, String name) {
        Map<String, Object> raw = (Map<String, Object>) schemas.get(name);
        Schema out = new Schema();
        merge(out, raw, schemas);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void merge(Schema out, Map<String, Object> raw, Map<String, Object> schemas) {
        if (raw == null) {
            return;
        }
        if (raw.containsKey("$ref")) {
            String ref = ((String) raw.get("$ref")).replace("#/components/schemas/", "");
            merge(out, (Map<String, Object>) schemas.get(ref), schemas);
            return;
        }
        for (Object part : (List<Object>) raw.getOrDefault("allOf", List.of())) {
            merge(out, (Map<String, Object>) part, schemas);
        }
        out.required.addAll((List<String>) raw.getOrDefault("required", List.of()));
        out.properties.putAll((Map<String, Object>) raw.getOrDefault("properties", Map.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSchemas() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(SPEC)) {
            Map<String, Object> spec = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
        }
    }

    private static final class Schema {
        final java.util.Set<String> required = new java.util.LinkedHashSet<>();
        final Map<String, Object> properties = new HashMap<>();
    }
}
