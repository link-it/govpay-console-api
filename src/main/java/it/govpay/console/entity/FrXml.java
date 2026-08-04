package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proiezione read-only della sola colonna {@code xml} di {@code fr}, mappata
 * come entity distinta da {@link Fr} sulla stessa tabella. Evita che il blob
 * venga caricato dalla query di lista (che seleziona {@link Fr} per intero):
 * letto solo quando il dettaglio è richiesto con {@code Accept: application/xml}.
 * Stessa ottimizzazione di {@code FrBD.findAllNoXml} in V1.
 */
@Entity
@Table(name = "fr")
public class FrXml {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "xml", columnDefinition = "BYTEA")
    private byte[] xml;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public byte[] getXml() {
        return xml;
    }

    public void setXml(byte[] xml) {
        this.xml = xml;
    }
}
