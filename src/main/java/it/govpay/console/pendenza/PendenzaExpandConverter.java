package it.govpay.console.pendenza;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import it.govpay.console.model.PendenzaExpand;

/**
 * Converter MVC per il query param {@code ?expand=}. L'enum {@code PendenzaExpand}
 * generato dall'OpenAPI Generator usa {@code @JsonValue} con il nome JSON
 * (es. {@code datiAllegati}), non il nome Java dell'enum (es. {@code DATI_ALLEGATI}).
 * Il converter standard di Spring usa {@code Enum.valueOf(name)}: fallirebbe.
 */
@Component
public class PendenzaExpandConverter implements Converter<String, PendenzaExpand> {

    @Override
    public PendenzaExpand convert(String source) {
        return PendenzaExpand.fromValue(source);
    }
}
