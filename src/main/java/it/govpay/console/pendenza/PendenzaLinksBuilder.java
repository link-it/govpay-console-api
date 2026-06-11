package it.govpay.console.pendenza;

import java.util.Set;

import org.springframework.stereotype.Component;

import it.govpay.console.model.Link;
import it.govpay.console.model.PendenzaLinks;
import it.govpay.console.model.StatoPendenza;

/**
 * Costruisce {@link PendenzaLinks} secondo le regole dello scope C di
 * issue #9:
 * <ul>
 *   <li>{@code informazioniDebitore}: sempre presente;</li>
 *   <li>{@code avviso}: presente solo se la pendenza ha {@code numeroAvviso};</li>
 *   <li>{@code ricevuta}: presente solo se la pendenza ha almeno un
 *       pagamento riuscito (stato in PAGATA, PAGATA_PARZIALE, RICONCILIATA).</li>
 * </ul>
 * Gli href sono relativi al base path del servizio.
 */
@Component
public class PendenzaLinksBuilder {

    private static final Set<StatoPendenza> STATI_CON_RICEVUTA = Set.of(
            StatoPendenza.PAGATA,
            StatoPendenza.PAGATA_PARZIALE,
            StatoPendenza.RICONCILIATA);

    public PendenzaLinks build(String idA2A, String idPendenza, String numeroAvviso, StatoPendenza stato) {
        String base = "/pendenze/" + idA2A + "/" + idPendenza;
        PendenzaLinks links = new PendenzaLinks(href(base + "/informazioniDebitore"));
        if (numeroAvviso != null && !numeroAvviso.isBlank()) {
            links.avviso(href(base + "/avviso"));
        }
        if (stato != null && STATI_CON_RICEVUTA.contains(stato)) {
            links.ricevuta(href(base + "/ricevuta"));
        }
        return links;
    }

    private static Link href(String value) {
        return new Link(value);
    }
}
