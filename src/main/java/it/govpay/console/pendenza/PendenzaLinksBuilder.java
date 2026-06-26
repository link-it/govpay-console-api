package it.govpay.console.pendenza;

import org.springframework.stereotype.Component;

import it.govpay.console.model.Link;
import it.govpay.console.model.PendenzaLinks;
import it.govpay.console.model.StatoPendenza;

/**
 * Costruisce {@link PendenzaLinks}:
 * <ul>
 *   <li>{@code informazioniDebitore}: sempre presente;</li>
 *   <li>{@code ricevute}: sempre presente (la lista e' eventualmente vuota);</li>
 *   <li>{@code avviso}: presente solo se la pendenza ha {@code numeroAvviso}.</li>
 * </ul>
 * Gli href sono relativi al base path del servizio.
 */
@Component
public class PendenzaLinksBuilder {

    public PendenzaLinks build(String idA2A, String idPendenza, String numeroAvviso, StatoPendenza stato) {
        String base = "/pendenze/" + idA2A + "/" + idPendenza;
        PendenzaLinks links = new PendenzaLinks(
                href(base + "/informazioniDebitore"),
                href(base + "/ricevute"));
        if (numeroAvviso != null && !numeroAvviso.isBlank()) {
            links.avviso(href(base + "/avviso"));
        }
        return links;
    }

    private static Link href(String value) {
        return new Link(value);
    }
}
