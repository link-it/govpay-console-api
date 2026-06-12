package it.govpay.console.ricevuta;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.Rpt;
import it.govpay.console.entity.SingoloVersamento;
import it.govpay.console.entity.Versamento;
import it.govpay.console.model.PspRef;
import it.govpay.console.model.Ricevuta;
import it.govpay.console.model.RicevutaSingoliVersamentiInner;

/**
 * Mapper {@link Rpt} → {@link Ricevuta} per la variante {@code application/json}
 * dell'endpoint {@code /pendenze/{idA2A}/{idPendenza}/ricevuta}.
 *
 * <p>Schema {@code Ricevuta} V2: metadati locali (no conversione XML→JSON).
 * Allineato all'approccio "fonte autorevole DB" (non riparsiamo l'xml_rt per
 * costruire il JSON: l'XML originale viene servito solo sulla variante
 * {@code application/xml}).
 *
 * <p>Per {@code singoliVersamenti[].iur} usiamo {@code indiceDati} del
 * singolo versamento (allineato a {@code link-it/govpay-portal-api}
 * {@code StampeMapper.toReceipt}). E' una semplificazione esplicita rispetto
 * all'IUR PagoPA presente solo nell'XML RT.
 */
@Component
public class RicevutaMapper {

    public Ricevuta toRicevuta(Rpt rpt) {
        if (rpt == null) {
            return null;
        }
        Ricevuta r = new Ricevuta(
                rpt.getIuv(),
                rpt.getCcp(),
                rpt.getCodDominio(),
                rpt.getDataMsgRicevuta(),
                rpt.getImportoTotalePagato());
        r.setPsp(mapPsp(rpt));
        r.setRiferimentoTransazione(rpt.getCodTransazioneRt());
        Versamento v = rpt.getVersamento();
        if (v != null) {
            r.setCausale(v.getCausaleVersamento());
            r.setSingoliVersamenti(mapSingoliVersamenti(v));
        }
        return r;
    }

    private static PspRef mapPsp(Rpt rpt) {
        if (rpt.getCodPsp() == null && rpt.getDenominazioneAttestante() == null) {
            return null;
        }
        PspRef ref = new PspRef(rpt.getCodPsp() != null ? rpt.getCodPsp() : "");
        ref.setRagioneSociale(rpt.getDenominazioneAttestante());
        return ref;
    }

    private static List<RicevutaSingoliVersamentiInner> mapSingoliVersamenti(Versamento v) {
        if (v.getSingoliVersamenti() == null || v.getSingoliVersamenti().isEmpty()) {
            return null;
        }
        List<RicevutaSingoliVersamentiInner> out = new ArrayList<>(v.getSingoliVersamenti().size());
        for (SingoloVersamento sv : v.getSingoliVersamenti()) {
            RicevutaSingoliVersamentiInner item = new RicevutaSingoliVersamentiInner();
            item.setIur(sv.getIndiceDati() != null ? String.valueOf(sv.getIndiceDati()) : null);
            item.setImporto(sv.getImportoSingoloVersamento());
            item.setCausale(sv.getDescrizione());
            out.add(item);
        }
        return out;
    }
}
