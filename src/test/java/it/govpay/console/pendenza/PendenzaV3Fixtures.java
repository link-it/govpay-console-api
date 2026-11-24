package it.govpay.console.pendenza;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import it.govpay.pendenze.entity.Debitore;
import it.govpay.pendenze.entity.OpzionePagamento;
import it.govpay.pendenze.entity.PdDebitore;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.PosizioneDebitoria;
import it.govpay.pendenze.entity.VocePendenza;
import it.govpay.pendenze.model.StatoOpzionePagamento;
import it.govpay.pendenze.model.StatoPendenza;
import it.govpay.pendenze.model.StatoVocePendenza;
import it.govpay.pendenze.model.TipoSoggetto;
import it.govpay.pendenze.model.TipologiaOpzionePagamento;
import jakarta.persistence.EntityManager;

/**
 * Costruisce nei test pendenze del modello v3: una posizione debitoria con un debitore,
 * una opzione di pagamento e la pendenza.
 */
public final class PendenzaV3Fixtures {

    /** Chiavi e valori con cui creare una pendenza; i campi non obbligatori possono restare {@code null}. */
    public record Dati(
            Long idApplicazione,
            Long idDominio,
            Long idTipoVersamento,
            Long idTipoPendenza,
            Long idUnitaOperativa,
            String idPendenza,
            String numeroAvviso,
            String debitore,
            double importo,
            StatoPendenza stato,
            int oreDaOra,
            LocalDate dataPubblicazione,
            OffsetDateTime dataScadenza,
            TipologiaOpzionePagamento tipologia,
            String datiAllegati,
            String proprieta) {

        public Dati(Long idApplicazione, Long idDominio, Long idTipoVersamento, Long idTipoPendenza,
             Long idUnitaOperativa, String idPendenza, String numeroAvviso, String debitore,
             double importo, StatoPendenza stato, int oreDaOra) {
            this(idApplicazione, idDominio, idTipoVersamento, idTipoPendenza, idUnitaOperativa, idPendenza,
                    numeroAvviso, debitore, importo, stato, oreDaOra, null, null,
                    TipologiaOpzionePagamento.SOLUZIONE_UNICA, null, null);
        }

        Dati conDatiAllegati(String valore, String proprietaValore) {
            return new Dati(idApplicazione, idDominio, idTipoVersamento, idTipoPendenza, idUnitaOperativa,
                    idPendenza, numeroAvviso, debitore, importo, stato, oreDaOra, dataPubblicazione,
                    dataScadenza, tipologia, valore, proprietaValore);
        }

        Dati conPubblicazione(LocalDate data) {
            return new Dati(idApplicazione, idDominio, idTipoVersamento, idTipoPendenza, idUnitaOperativa,
                    idPendenza, numeroAvviso, debitore, importo, stato, oreDaOra, data, dataScadenza, tipologia,
                    datiAllegati, proprieta);
        }

        Dati conScadenza(OffsetDateTime data) {
            return new Dati(idApplicazione, idDominio, idTipoVersamento, idTipoPendenza, idUnitaOperativa,
                    idPendenza, numeroAvviso, debitore, importo, stato, oreDaOra, dataPubblicazione, data,
                    tipologia, datiAllegati, proprieta);
        }

        public Dati conTipologia(TipologiaOpzionePagamento valore) {
            return new Dati(idApplicazione, idDominio, idTipoVersamento, idTipoPendenza, idUnitaOperativa,
                    idPendenza, numeroAvviso, debitore, importo, stato, oreDaOra, dataPubblicazione,
                    dataScadenza, valore, datiAllegati, proprieta);
        }
    }

    private PendenzaV3Fixtures() {
    }

    /** Persiste la posizione con la pendenza, in una nuova posizione per ogni pendenza. */
    public static Pendenza pendenza(EntityManager em, Dati dati) {
        return pendenza(em, dati, null, false);
    }

    /**
     * Come {@link #pendenza(EntityManager, Dati)}, ma la posizione ha prima un altro debitore
     * ({@code primoDebitore}) e la pendenza appartiene al secondo; con {@code rimosso} il
     * debitore della pendenza e' rimosso e la sua opzione annullata.
     */
    public static Pendenza pendenzaDelSecondoDebitore(EntityManager em, Dati dati, String primoDebitore, boolean rimosso) {
        return pendenza(em, dati, primoDebitore, rimosso);
    }

    private static Pendenza pendenza(EntityManager em, Dati dati, String primoDebitore, boolean rimosso) {
        OffsetDateTime riferimento = OffsetDateTime.now().minusHours(dati.oreDaOra());

        PosizioneDebitoria posizione = new PosizioneDebitoria();
        posizione.setIdApplicazione(dati.idApplicazione());
        posizione.setIdDominio(dati.idDominio());
        posizione.setIdUnitaOperativa(dati.idUnitaOperativa());
        posizione.setCodDocumento("DOC-" + dati.idPendenza());
        posizione.setDescrizione("Posizione " + dati.idPendenza());
        posizione.setDataPubblicazione(dati.dataPubblicazione());
        posizione.setDataCreazione(riferimento);
        posizione.setDataUltimoAggiornamento(riferimento);

        if (primoDebitore != null) {
            Debitore primo = new Debitore();
            primo.setTipo(TipoSoggetto.F);
            primo.setIdentificativo(primoDebitore);
            primo.setAnagrafica("Anagrafica " + primoDebitore);
            PdDebitore pdPrimo = new PdDebitore();
            pdPrimo.setPosizione(0);
            pdPrimo.setDebitore(primo);
            posizione.addPdDebitore(pdPrimo);
        }
        Debitore debitore = new Debitore();
        debitore.setTipo(primoDebitore != null ? TipoSoggetto.G : TipoSoggetto.F);
        debitore.setIdentificativo(dati.debitore());
        debitore.setAnagrafica("Anagrafica " + dati.debitore());
        PdDebitore pdDebitore = new PdDebitore();
        pdDebitore.setPosizione(primoDebitore != null ? 1 : 0);
        pdDebitore.setDebitore(debitore);
        if (rimosso) {
            pdDebitore.setDataRimozione(riferimento);
        }
        posizione.addPdDebitore(pdDebitore);

        OpzionePagamento opzione = nuovaOpzione(dati, riferimento, rimosso);
        pdDebitore.addOpzionePagamento(opzione);
        Pendenza pendenza = nuovaPendenza(dati, riferimento);
        opzione.addPendenza(pendenza);

        em.persist(posizione);
        return pendenza;
    }

    private static OpzionePagamento nuovaOpzione(Dati dati, OffsetDateTime riferimento, boolean rimosso) {
        OpzionePagamento opzione = new OpzionePagamento();
        opzione.setIdOpzionePagamento(UUID.randomUUID());
        opzione.setTipologia(dati.tipologia());
        opzione.setStato(rimosso ? StatoOpzionePagamento.ANNULLATA : StatoOpzionePagamento.DISPONIBILE);
        opzione.setDataCreazione(riferimento);
        opzione.setDataUltimoAggiornamento(riferimento);
        return opzione;
    }

    private static Pendenza nuovaPendenza(Dati dati, OffsetDateTime riferimento) {
        Pendenza pendenza = new Pendenza();
        pendenza.setIdApplicazione(dati.idApplicazione());
        pendenza.setIdDominio(dati.idDominio());
        pendenza.setIdTipoVersamento(dati.idTipoVersamento());
        pendenza.setIdTipoPendenza(dati.idTipoPendenza());
        pendenza.setIdPendenza(dati.idPendenza());
        pendenza.setNumeroRata(1);
        pendenza.setImporto(dati.importo());
        pendenza.setNumeroAvviso(dati.numeroAvviso());
        pendenza.setIuv(dati.numeroAvviso());
        pendenza.setSrcIuv(dati.numeroAvviso() == null ? null : dati.numeroAvviso().toUpperCase());
        pendenza.setDebitoreIdentificativo(dati.debitore());
        pendenza.setDebitoreAnagrafica("Anagrafica " + dati.debitore());
        pendenza.setSrcDebitoreIdentificativo(dati.debitore());
        pendenza.setStato(dati.stato());
        pendenza.setCausaleVersamento("Causale per " + dati.idPendenza());
        pendenza.setDirezione("Dir-X");
        pendenza.setDivisione("Div-Y");
        pendenza.setDataScadenzaAvviso(dati.dataScadenza());
        pendenza.setDatiAllegati(dati.datiAllegati());
        pendenza.setProprieta(dati.proprieta());
        pendenza.setDataCreazione(riferimento);
        pendenza.setDataUltimoAggiornamento(riferimento);

        return pendenza;
    }

    /** Aggiunge alla posizione di {@code esistente} una nuova opzione di pagamento, con la sua pendenza, per lo stesso debitore. */
    public static Pendenza pendenzaNellaStessaPosizione(EntityManager em, Pendenza esistente, Dati dati) {
        OffsetDateTime riferimento = OffsetDateTime.now().minusHours(dati.oreDaOra());
        OpzionePagamento opzione = nuovaOpzione(dati, riferimento, false);
        esistente.getOpzionePagamento().getPdDebitore().addOpzionePagamento(opzione);
        Pendenza pendenza = nuovaPendenza(dati, riferimento);
        opzione.addPendenza(pendenza);
        em.persist(opzione);
        return pendenza;
    }

    /** Aggiunge a {@code esistente} una rata nella sua stessa opzione di pagamento. */
    public static Pendenza rataNellaStessaOpzione(EntityManager em, Pendenza esistente, Dati dati, int numeroRata) {
        OffsetDateTime riferimento = OffsetDateTime.now().minusHours(dati.oreDaOra());
        Pendenza rata = nuovaPendenza(dati, riferimento);
        rata.setNumeroRata(numeroRata);
        esistente.getOpzionePagamento().addPendenza(rata);
        em.persist(rata);
        return rata;
    }

    /** Persiste la sola pendenza, senza opzione di pagamento ne' posizione debitoria. */
    public static Pendenza pendenzaSenzaOpzione(EntityManager em, Dati dati) {
        OffsetDateTime riferimento = OffsetDateTime.now().minusHours(dati.oreDaOra());
        Pendenza pendenza = new Pendenza();
        pendenza.setIdApplicazione(dati.idApplicazione());
        pendenza.setIdDominio(dati.idDominio());
        pendenza.setIdTipoVersamento(dati.idTipoVersamento());
        pendenza.setIdTipoPendenza(dati.idTipoPendenza());
        pendenza.setIdPendenza(dati.idPendenza());
        pendenza.setNumeroRata(1);
        pendenza.setImporto(dati.importo());
        pendenza.setNumeroAvviso(dati.numeroAvviso());
        pendenza.setDebitoreIdentificativo(dati.debitore());
        pendenza.setDebitoreAnagrafica("Anagrafica " + dati.debitore());
        pendenza.setSrcDebitoreIdentificativo(dati.debitore());
        pendenza.setStato(dati.stato());
        pendenza.setDataCreazione(riferimento);
        pendenza.setDataUltimoAggiornamento(riferimento);
        em.persist(pendenza);
        return pendenza;
    }

    /** Aggiunge e persiste una voce non eseguita; {@code personalizza} ne imposta il riferimento. */
    static VocePendenza voce(EntityManager em, Pendenza pendenza, String idVoce, int indice, double importo,
                             String descrizione, Consumer<VocePendenza> personalizza) {
        VocePendenza voce = new VocePendenza();
        voce.setIdVocePendenza(idVoce);
        voce.setIndice(indice);
        voce.setImporto(importo);
        voce.setDescrizione(descrizione);
        voce.setStato(StatoVocePendenza.NON_ESEGUITO);
        if (personalizza != null) {
            personalizza.accept(voce);
        }
        voce.setPendenza(pendenza);
        em.persist(voce);
        return voce;
    }
}
