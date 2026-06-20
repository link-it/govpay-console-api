package it.govpay.console.stazione;

import it.govpay.console.web.UnprocessableEntityException;

/**
 * Validazione e parsing del formato {@code idStazione = {idIntermediario}_{applicationCode}}.
 * L'{@code idStazione} deve iniziare esattamente con {@code idIntermediario + "_"}
 * (cosi' il parsing e' corretto anche quando l'{@code idIntermediario} contiene
 * {@code _}); l'{@code applicationCode} (intero 1-99) e' il suffisso residuo.
 * Le violazioni producono 422.
 */
public final class StazioneIdFormat {

    private StazioneIdFormat() {
    }

    public static int applicationCode(String idStazione, String idIntermediario) {
        String prefix = idIntermediario + "_";
        if (!idStazione.startsWith(prefix)) {
            throw new UnprocessableEntityException(
                    "'idStazione' non coerente: previsto formato '" + idIntermediario + "_{applicationCode}'.");
        }
        String suffix = idStazione.substring(prefix.length());
        int applicationCode;
        try {
            applicationCode = Integer.parseInt(suffix);
        } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                    "'applicationCode' non numerico in 'idStazione': '" + suffix + "'.");
        }
        if (applicationCode < 1 || applicationCode > 99) {
            throw new UnprocessableEntityException(
                    "'applicationCode' deve essere compreso tra 1 e 99 (trovato " + applicationCode + ").");
        }
        return applicationCode;
    }
}
