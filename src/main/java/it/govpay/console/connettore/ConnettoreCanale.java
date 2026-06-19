package it.govpay.console.connettore;

import java.util.function.BiConsumer;
import java.util.function.Function;

import it.govpay.console.entity.Intermediario;

/**
 * I canali di connettore pagoPA di un intermediario. Ogni canale conosce:
 * il suffisso con cui si costruisce il proprio {@code cod_connettore} a partire
 * dall'idIntermediario, se ha la shape "pagopa" (urlRPT) o quella secondaria
 * (url + abilitaGDE), e come leggere/scrivere il riferimento {@code cod_connettore_*}
 * sull'entity {@link Intermediario}.
 */
public enum ConnettoreCanale {

    PAGOPA("", true, Intermediario::getCodConnettorePdd, Intermediario::setCodConnettorePdd),
    ACA("_ACA", false, Intermediario::getCodConnettoreAca, Intermediario::setCodConnettoreAca),
    GPD("_GPD", false, Intermediario::getCodConnettoreGpd, Intermediario::setCodConnettoreGpd),
    FR("_FR", false, Intermediario::getCodConnettoreFr, Intermediario::setCodConnettoreFr),
    BACKOFFICE_EC("_BOEC", false, Intermediario::getCodConnettoreBackofficeEc, Intermediario::setCodConnettoreBackofficeEc),
    RECUPERO_RT("_RT", false, Intermediario::getCodConnettoreRecuperoRt, Intermediario::setCodConnettoreRecuperoRt);

    private final String suffix;
    private final boolean pagopaShape;
    private final Function<Intermediario, String> codGetter;
    private final BiConsumer<Intermediario, String> codSetter;

    ConnettoreCanale(String suffix, boolean pagopaShape,
                     Function<Intermediario, String> codGetter,
                     BiConsumer<Intermediario, String> codSetter) {
        this.suffix = suffix;
        this.pagopaShape = pagopaShape;
        this.codGetter = codGetter;
        this.codSetter = codSetter;
    }

    public boolean isPagopaShape() {
        return pagopaShape;
    }

    /** Riferimento {@code cod_connettore} corrente sull'intermediario, o {@code null} se lo slot e' vuoto. */
    public String codConnettore(Intermediario intermediario) {
        return codGetter.apply(intermediario);
    }

    /** Imposta il riferimento {@code cod_connettore} sull'intermediario. */
    public void setCodConnettore(Intermediario intermediario, String codConnettore) {
        codSetter.accept(intermediario, codConnettore);
    }

    /** {@code cod_connettore} da generare per questo canale quando lo slot e' vuoto. */
    public String generaCodConnettore(Intermediario intermediario) {
        return intermediario.getCodIntermediario() + suffix;
    }
}
