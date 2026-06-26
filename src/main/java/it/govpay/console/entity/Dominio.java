package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "domini")
@SequenceGenerator(name = "seq_domini", sequenceName = "seq_domini", allocationSize = 1)
public class Dominio {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_domini")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "ragione_sociale", nullable = false, length = 70)
    private String ragioneSociale;

    @Column(name = "aux_digit", nullable = false)
    private Integer auxDigit;

    @Column(name = "gln", length = 35)
    private String gln;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_stazione")
    private Stazione stazione;

    /**
     * Logo dell'ente creditore in base64 ASCII (es. {@code data:image/png;base64,...}).
     * Usato per il PDF della ricevuta (campo {@code creditor_logo} del payload del
     * microservizio {@code govpay-stampe}).
     */
    @Column(name = "logo", columnDefinition = "BYTEA")
    private byte[] logo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
    }

    public String getRagioneSociale() {
        return ragioneSociale;
    }

    public void setRagioneSociale(String ragioneSociale) {
        this.ragioneSociale = ragioneSociale;
    }

    public Integer getAuxDigit() {
        return auxDigit;
    }

    public void setAuxDigit(Integer auxDigit) {
        this.auxDigit = auxDigit;
    }

    public String getGln() {
        return gln;
    }

    public void setGln(String gln) {
        this.gln = gln;
    }

    public Stazione getStazione() {
        return stazione;
    }

    public void setStazione(Stazione stazione) {
        this.stazione = stazione;
    }

    public byte[] getLogo() {
        return logo;
    }

    public void setLogo(byte[] logo) {
        this.logo = logo;
    }
}
