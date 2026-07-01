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

/**
 * Conto di accredito (IBAN) di un dominio. Oltre ai campi usati altrove
 * ({@code postale} — l'avviso PDF lo usa per {@code postal=true} sul payload di
 * {@code govpay-stampe} — e {@code codIban}, esposto nelle voci di incasso
 * diretto), porta l'anagrafica gestita dal CRUD dei conti accredito. Univoco
 * per {@code (cod_iban, id_dominio)}.
 */
@Entity
@Table(name = "iban_accredito")
@SequenceGenerator(name = "seq_iban_accredito", sequenceName = "seq_iban_accredito", allocationSize = 1)
public class IbanAccredito {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_iban_accredito")
    @Column(name = "id")
    private Long id;

    @Column(name = "postale", nullable = false)
    private Boolean postale;

    @Column(name = "cod_iban", nullable = false, length = 255)
    private String codIban;

    @Column(name = "bic_accredito", length = 255)
    private String bicAccredito;

    @Column(name = "abilitato", nullable = false)
    private Boolean abilitato = Boolean.TRUE;

    @Column(name = "descrizione", length = 255)
    private String descrizione;

    @Column(name = "intestatario", length = 255)
    private String intestatario;

    @Column(name = "aut_stampa_poste", length = 255)
    private String autStampaPoste;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getPostale() {
        return postale;
    }

    public void setPostale(Boolean postale) {
        this.postale = postale;
    }

    public String getCodIban() {
        return codIban;
    }

    public void setCodIban(String codIban) {
        this.codIban = codIban;
    }

    public String getBicAccredito() {
        return bicAccredito;
    }

    public void setBicAccredito(String bicAccredito) {
        this.bicAccredito = bicAccredito;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public String getIntestatario() {
        return intestatario;
    }

    public void setIntestatario(String intestatario) {
        this.intestatario = intestatario;
    }

    public String getAutStampaPoste() {
        return autStampaPoste;
    }

    public void setAutStampaPoste(String autStampaPoste) {
        this.autStampaPoste = autStampaPoste;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }
}
