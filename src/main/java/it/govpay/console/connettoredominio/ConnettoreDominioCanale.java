package it.govpay.console.connettoredominio;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import it.govpay.console.entity.Dominio;

/**
 * I canali di connettore di notifica pagamenti di un dominio. Ogni canale
 * conosce: il proprio {@code Tipo} (usato per costruire {@code cod_connettore} e
 * come valore della proprieta' {@code TIPO_TRACCIATO}), come leggere/scrivere il
 * riferimento {@code cod_connettore_*} sull'entity {@link Dominio}, se prevede
 * credenziali (auth) e se la sua persistenza e' quella speciale di Maggioli JPPA
 * (tabella {@code jppa_config}, senza colonna su {@code domini}).
 */
public enum ConnettoreDominioCanale {

    MYPIVOT("MYPIVOT", false, Dominio::getCodConnettoreMyPivot, Dominio::setCodConnettoreMyPivot),
    SECIM("SECIM", false, Dominio::getCodConnettoreSecim, Dominio::setCodConnettoreSecim),
    GOVPAY("GOVPAY", true, Dominio::getCodConnettoreGovPay, Dominio::setCodConnettoreGovPay),
    HYPER_SIC_APKAPPA("HYPER_SIC_APKAPPA", false, Dominio::getCodConnettoreHyperSicApk, Dominio::setCodConnettoreHyperSicApk),
    MAGGIOLI_JPPA("MAGGIOLI_JPPA", true, null, null),
    /**
     * Connettore verso il servizio SEND (issue #22, integrazione govpay-core
     * #721): attualizzazione dell'importo con le spese di notifica. A
     * differenza degli altri 5 canali (tutti {@code DOM_<codDominio>_<TIPO>})
     * il {@code cod_connettore} di SEND e' {@code <codDominio>_SEND} — verificato
     * su {@code DominiBD.getIDConnettoreSend} lato govpay-core, format diverso
     * gia' fissato li' e non modificabile da qui.
     */
    SEND("SEND", true, Dominio::getCodConnettoreSend, Dominio::setCodConnettoreSend,
            codDominio -> codDominio + "_SEND");

    private final String tipo;
    private final boolean credenziali;
    private final Function<Dominio, String> codGetter;
    private final BiConsumer<Dominio, String> codSetter;
    private final UnaryOperator<String> codConnettoreFormat;

    ConnettoreDominioCanale(String tipo, boolean credenziali,
                            Function<Dominio, String> codGetter,
                            BiConsumer<Dominio, String> codSetter) {
        this(tipo, credenziali, codGetter, codSetter, codDominio -> "DOM_" + codDominio + "_" + tipo);
    }

    ConnettoreDominioCanale(String tipo, boolean credenziali,
                            Function<Dominio, String> codGetter,
                            BiConsumer<Dominio, String> codSetter,
                            UnaryOperator<String> codConnettoreFormat) {
        this.tipo = tipo;
        this.credenziali = credenziali;
        this.codGetter = codGetter;
        this.codSetter = codSetter;
        this.codConnettoreFormat = codConnettoreFormat;
    }

    /** Valore della proprieta' {@code TIPO_TRACCIATO} e suffisso del {@code cod_connettore}. */
    public String tipo() {
        return tipo;
    }

    public boolean hasCredenziali() {
        return credenziali;
    }

    /** Maggioli JPPA ha persistenza dedicata ({@code jppa_config}), non una colonna su {@code domini}. */
    public boolean isMaggioliJppa() {
        return this == MAGGIOLI_JPPA;
    }

    /** Riferimento {@code cod_connettore} corrente sul dominio, o {@code null}. Non applicabile a Maggioli JPPA. */
    public String codConnettore(Dominio dominio) {
        return codGetter.apply(dominio);
    }

    /** Imposta il riferimento {@code cod_connettore} sul dominio. Non applicabile a Maggioli JPPA. */
    public void setCodConnettore(Dominio dominio, String codConnettore) {
        codSetter.accept(dominio, codConnettore);
    }

    /** {@code cod_connettore} da generare per questo canale: {@code DOM_<codDominio>_<TIPO>}, tranne SEND. */
    public String generaCodConnettore(String codDominio) {
        return codConnettoreFormat.apply(codDominio);
    }
}
