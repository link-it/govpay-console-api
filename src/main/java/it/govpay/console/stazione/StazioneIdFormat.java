package it.govpay.console.stazione;

import it.govpay.console.web.UnprocessableEntityException;

/**
 * Validazione e parsing del formato {@code idStazione = {idIntermediario}_{applicationCode}}.
 * L'{@code applicationCode} (intero 1-99) viene derivato dal suffisso dopo il primo
 * {@code _}; il prefisso deve coincidere con l'{@code idIntermediario} del path.
 * Le violazioni producono 422 (allineato V1).
 */
public final class StazioneIdFormat {

    private StazioneIdFormat() {
    }

    public static int applicationCode(String idStazione, String idIntermediario) {
        int idx = idStazione.indexOf('_');
        if (idx == -1) {
            throw new UnprocessableEntityException(
                    "Formato 'idStazione' non valido: previsto {idIntermediario}_{applicationCode}.");
        }
        String base = idStazione.substring(0, idx);
        if (!base.equals(idIntermediario)) {
            throw new UnprocessableEntityException(
                    "'idStazione' non coerente: il prefisso deve essere l'idIntermediario '" + idIntermediario + "'.");
        }
        String suffix = idStazione.substring(idx + 1);
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
