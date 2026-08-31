package it.govpay.console.security;

import java.util.Set;

/**
 * Snapshot dell'operatore autenticato per la richiesta corrente.
 * Esposto da {@link CurrentOperatorService} ai service di dominio.
 *
 * @param principal               username Spring Security (= utenze.principal)
 * @param idUtenza                utenze.id
 * @param idOperatore             operatori.id (o null se l'utenza non ha un operatore collegato)
 * @param nomeOperatore           operatori.nome (o null)
 * @param tuttiIDomini            utenze.autorizzazione_domini_star
 * @param idDominiInteri          domini in cui l'utenza vede TUTTE le UO
 *                                (utenze_domini con id_uo IS NULL)
 * @param idUoVisibili            UO specifiche su cui l'utenza ha visibilita'
 *                                (utenze_domini con id_uo IS NOT NULL)
 * @param idDominiRaggiungibili   {@code idDominiInteri} unito ai domini che
 *                                contengono almeno una UO in {@code idUoVisibili}
 *                                (V1: {@code Utenza.getIdDomini()}/{@code isDominioAuthorized}
 *                                considera "autorizzato" un dominio anche con
 *                                un solo grant parziale). Criterio piu' largo di
 *                                {@code idDominiInteri}: usarlo solo dove la
 *                                risorsa ha un fallback a grana UO (es. la lista
 *                                dei domini stessa, o le sue unita' operative);
 *                                le risorse senza asse UO (es. flussi di
 *                                rendicontazione) restano su {@code idDominiInteri}
 *                                via {@link DominioVisibilita}.
 * @param tuttiITipiVersamento    utenze.autorizzazione_tipi_vers_star
 * @param idTipiVersamentoVisibili tipi versamento su cui l'utenza ha visibilita'
 *                                (utenze_tipo_vers); significativo solo se
 *                                tuttiITipiVersamento=false
 */
public record OperatoreCorrente(
        String principal,
        long idUtenza,
        Long idOperatore,
        String nomeOperatore,
        boolean tuttiIDomini,
        Set<Long> idDominiInteri,
        Set<Long> idUoVisibili,
        Set<Long> idDominiRaggiungibili,
        boolean tuttiITipiVersamento,
        Set<Long> idTipiVersamentoVisibili) {
}
