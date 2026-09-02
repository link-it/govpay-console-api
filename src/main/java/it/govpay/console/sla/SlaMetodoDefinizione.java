package it.govpay.console.sla;

import it.govpay.console.model.SlaKpiCodice;

/**
 * Catalogo statico dei 4 metodi PA pagoPA con SLA di tempo di risposta
 * (issue #36): soglia 2s al 98° percentile per tutti e quattro, per contratto
 * pagoPA — non una scelta di questo progetto.
 */
public enum SlaMetodoDefinizione {

    TDP(SlaKpiCodice.TDP, "paDemandPaymentNotice"),
    TGP(SlaKpiCodice.TGP, "paGetPayment"),
    TSRT(SlaKpiCodice.TSRT, "paSendRT"),
    TVP(SlaKpiCodice.TVP, "paVerifyPaymentNotice");

    public static final double SOGLIA_SECONDI = 2.0;
    public static final int SOGLIA_PERCENTILE = 98;

    private final SlaKpiCodice codice;
    private final String metodo;

    SlaMetodoDefinizione(SlaKpiCodice codice, String metodo) {
        this.codice = codice;
        this.metodo = metodo;
    }

    public SlaKpiCodice codice() {
        return codice;
    }

    public String metodo() {
        return metodo;
    }
}
