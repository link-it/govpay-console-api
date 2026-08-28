package it.govpay.console.unitaoperativa;

import it.govpay.console.entity.UnitaOperativa;

/**
 * Anagrafica di una unita' operativa, nei tredici campi che i DTO di dominio e di
 * unita' operativa espongono con gli stessi nomi.
 * <p>
 * Raccolti in un record perche' erano tredici parametri {@code String} posizionali,
 * scritti da due metodi identici in {@code DominioService} (sulla UO "EC" del
 * dominio) e in {@code UnitaOperativaService}: tredici setter duplicati, e una
 * trasposizione fra due parametri adiacenti non avrebbe prodotto alcun errore
 * visibile.
 */
public record AnagraficaUo(String ragioneSociale, String indirizzo, String civico, String cap,
        String localita, String provincia, String nazione, String email, String pec,
        String tel, String fax, String web, String area) {

    /** Applica l'anagrafica sull'entity, senza toccarne gli altri campi. */
    public void applicaSu(UnitaOperativa e) {
        e.setUoDenominazione(ragioneSociale);
        e.setUoIndirizzo(indirizzo);
        e.setUoCivico(civico);
        e.setUoCap(cap);
        e.setUoLocalita(localita);
        e.setUoProvincia(provincia);
        e.setUoNazione(nazione);
        e.setUoEmail(email);
        e.setUoPec(pec);
        e.setUoTel(tel);
        e.setUoFax(fax);
        e.setUoUrlSitoWeb(web);
        e.setUoArea(area);
    }
}
