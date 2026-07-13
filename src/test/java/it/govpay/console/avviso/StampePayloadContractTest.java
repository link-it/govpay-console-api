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

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.console.entity.Dominio;
import it.govpay.console.entity.Stazione;
import it.govpay.console.entity.Versamento;
import it.govpay.stampe.client.model.PaymentNotice;

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

    private final AvvisoPdfPayloadMapper mapper = new AvvisoPdfPayloadMapper();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    @SuppressWarnings("unchecked")
    void paymentNoticeSatisfiesStampeRequiredFields() throws Exception {
        Map<String, Object> schemas = loadSchemas();
        Map<String, Object> payload = json.convertValue(mapper.toPaymentNotice(sample(), null), Map.class);

        assertRequired(payload, resolve(schemas, "PaymentNotice"), "PaymentNotice");
        assertRequired((Map<String, Object>) payload.get("full"), resolve(schemas, "Amount"), "Amount");
        assertRequired((Map<String, Object>) payload.get("creditor"), resolve(schemas, "Creditor"), "Creditor");
        assertRequired((Map<String, Object>) payload.get("debtor"), resolve(schemas, "Debtor"), "Debtor");
    }

    private static Versamento sample() {
        Stazione stazione = new Stazione();
        stazione.setApplicationCode(1);
        Dominio dominio = new Dominio();
        dominio.setCodDominio("12345678901");
        dominio.setRagioneSociale("Comune di Test");
        dominio.setAuxDigit(0);
        dominio.setStazione(stazione);
        dominio.setLogo(Base64.getEncoder().encode("png".getBytes(StandardCharsets.UTF_8)));
        Versamento v = new Versamento();
        v.setDominio(dominio);
        v.setIuvVersamento("123456789012345");
        v.setNumeroAvviso("001123456789012345");
        v.setImportoTotale(100.0);
        v.setDataScadenza(OffsetDateTime.now().plusDays(30));
        v.setDebitoreIdentificativo("RSSMRA80A01H501U");
        v.setDebitoreAnagrafica("Mario Rossi");
        return v;
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
