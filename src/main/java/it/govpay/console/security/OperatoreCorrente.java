package it.govpay.console.security;

import java.util.List;

/**
 * Snapshot dell'operatore autenticato per la richiesta corrente.
 * Esposto da {@link CurrentOperatorService} ai service di dominio.
 *
 * @param principal       username Spring Security (= utenze.principal)
 * @param idUtenza        utenze.id
 * @param idOperatore     operatori.id (o null se l'utenza non ha un operatore collegato)
 * @param nomeOperatore   operatori.nome (o null)
 * @param tuttiIDomini    se true, l'utente vede tutti i domini (utenze.autorizzazione_domini_star)
 * @param idDominiVisibili lista dei domini visibili (significativa solo se tuttiIDomini=false)
 */
public record OperatoreCorrente(
        String principal,
        long idUtenza,
        Long idOperatore,
        String nomeOperatore,
        boolean tuttiIDomini,
        List<Long> idDominiVisibili) {
}
