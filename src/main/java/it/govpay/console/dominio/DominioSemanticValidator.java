package it.govpay.console.dominio;

import java.time.Year;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import it.govpay.console.web.UnprocessableEntityException;

/**
 * Validazione semantica del dominio, allineata a {@code DominioPost.validate} e
 * {@code DominioValidator} di V1 (esito 422). Copre i vincoli incrociati su
 * {@code intermediato} e la generazione del prefisso IUV, non esprimibili come
 * Bean Validation di singolo campo (quelli stanno nello schema OpenAPI).
 */
@Component
public class DominioSemanticValidator {

    /** Il prefisso IUV generato deve risultare numerico e al massimo 13 cifre. */
    private static final Pattern PREFIX_RISULTANTE = Pattern.compile("^[0-9]{1,13}$");

    /**
     * Valori massimi (in cifre) con cui V1 espande i placeholder del prefisso per
     * verificare che il risultato resti numerico e non troppo lungo.
     */
    private static final Map<String, String> PLACEHOLDER_MASSIMI = Map.of(
            "a", "1111",   // codifica IUV applicazione
            "u", "2222",   // codice UO beneficiaria
            "t", "3333",   // codifica IUV tributo
            "p", "3333");  // codifica IUV tipo pendenza

    /**
     * @param intermediato valore richiesto ({@code null} equivale a {@code true},
     *                     come in V1).
     */
    public void validate(Boolean intermediato, String gln, String idStazione, Integer segregationCode,
            String cbill, String autStampaPoste, String iuvPrefix, Integer auxDigit) {

        boolean intermediatoEff = intermediato == null || intermediato;
        if (intermediatoEff) {
            requireNotNull(gln, "gln");
            requireNotNull(idStazione, "idStazione");
            // auxDigit e' opzionale: assente significa 0 (intermediazione singola), coerente
            // col default NOT NULL della colonna. Il range 0-3 e' nello schema OpenAPI.

            // Dominio pluri-intermediato (auxDigit=3) deve avere il codice di segregazione.
            if (auxDigit != null && auxDigit == 3 && segregationCode == null) {
                throw new UnprocessableEntityException(
                        "Il campo 'segregationCode' e' obbligatorio quando 'auxDigit' vale 3 "
                                + "(dominio pluri-intermediato).");
            }
            validateIuvPrefix(iuvPrefix);
        } else {
            requireNull(gln, "gln");
            requireNull(idStazione, "idStazione");
            requireNull(segregationCode, "segregationCode");
            requireNull(cbill, "cbill");
            requireNull(autStampaPoste, "autStampaPosteItaliane");
            requireNull(iuvPrefix, "iuvPrefix");
            requireNull(auxDigit, "auxDigit");
        }
    }

    private void validateIuvPrefix(String iuvPrefix) {
        if (iuvPrefix == null || iuvPrefix.isEmpty()) {
            return;
        }
        String generato = espandiPrefix(iuvPrefix);
        if (generato.length() > 18 || !PREFIX_RISULTANTE.matcher(generato).matches()) {
            throw new UnprocessableEntityException(
                    "Il prefisso IUV '" + iuvPrefix + "' genera un valore non valido (atteso numerico, "
                            + "massimo 13 cifre): '" + generato + "'.");
        }
    }

    private String espandiPrefix(String template) {
        int anno = Year.now().getValue();
        String result = template;
        for (Map.Entry<String, String> e : PLACEHOLDER_MASSIMI.entrySet()) {
            result = result.replace("%(" + e.getKey() + ")", e.getValue());
        }
        result = result.replace("%(Y)", Integer.toString(anno));
        result = result.replace("%(y)", Integer.toString(anno % 100));
        return result;
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw new UnprocessableEntityException(
                    "Il campo '" + field + "' e' obbligatorio per un dominio intermediato.");
        }
    }

    private static void requireNull(Object value, String field) {
        if (value != null) {
            throw new UnprocessableEntityException(
                    "Il campo '" + field + "' deve essere assente per un dominio non intermediato "
                            + "(intermediato=false).");
        }
    }
}
