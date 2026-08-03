package it.govpay.console.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Riga di rendicontazione (un singolo pagamento/IUV rendicontato all'interno
 * di un {@link Fr}). Mappa la tabella {@code rendicontazioni} con i soli campi
 * usati da {@code GET /flussi-rendicontazione}: il filtro {@code ?iuv=} (join
 * su {@code id_fr}) e l'aggregato {@code dataInizio}/{@code dataFine} del
 * dettaglio ({@code MIN}/{@code MAX(data)} per flusso).
 */
@Entity
@Table(name = "rendicontazioni")
@SequenceGenerator(name = "seq_rendicontazioni", sequenceName = "seq_rendicontazioni", allocationSize = 1)
public class Rendicontazione {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_rendicontazioni")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_fr", nullable = false)
    private Long idFr;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "data")
    private OffsetDateTime data;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdFr() {
        return idFr;
    }

    public void setIdFr(Long idFr) {
        this.idFr = idFr;
    }

    public String getIuv() {
        return iuv;
    }

    public void setIuv(String iuv) {
        this.iuv = iuv;
    }

    public OffsetDateTime getData() {
        return data;
    }

    public void setData(OffsetDateTime data) {
        this.data = data;
    }
}
