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
 * Entrata configurata per un dominio: associa il dominio al {@link TipoTributo}
 * (entrata globale) con eventuali override di contabilita' e i conti di
 * accredito/appoggio. Univoco per {@code (id_dominio, id_tipo_tributo)}.
 */
@Entity
@Table(name = "tributi")
@SequenceGenerator(name = "seq_tributi", sequenceName = "seq_tributi", allocationSize = 1)
public class Tributo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_tributi")
    @Column(name = "id")
    private Long id;

    @Column(name = "abilitato", nullable = false)
    private Boolean abilitato = Boolean.TRUE;

    @Column(name = "tipo_contabilita", length = 1)
    private String tipoContabilita;

    @Column(name = "codice_contabilita", length = 255)
    private String codiceContabilita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_dominio", nullable = false)
    private Dominio dominio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_tributo", nullable = false)
    private TipoTributo tipoTributo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_iban_accredito")
    private IbanAccredito ibanAccredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_iban_appoggio")
    private IbanAccredito ibanAppoggio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getAbilitato() {
        return abilitato;
    }

    public void setAbilitato(Boolean abilitato) {
        this.abilitato = abilitato;
    }

    public String getTipoContabilita() {
        return tipoContabilita;
    }

    public void setTipoContabilita(String tipoContabilita) {
        this.tipoContabilita = tipoContabilita;
    }

    public String getCodiceContabilita() {
        return codiceContabilita;
    }

    public void setCodiceContabilita(String codiceContabilita) {
        this.codiceContabilita = codiceContabilita;
    }

    public Dominio getDominio() {
        return dominio;
    }

    public void setDominio(Dominio dominio) {
        this.dominio = dominio;
    }

    public TipoTributo getTipoTributo() {
        return tipoTributo;
    }

    public void setTipoTributo(TipoTributo tipoTributo) {
        this.tipoTributo = tipoTributo;
    }

    public IbanAccredito getIbanAccredito() {
        return ibanAccredito;
    }

    public void setIbanAccredito(IbanAccredito ibanAccredito) {
        this.ibanAccredito = ibanAccredito;
    }

    public IbanAccredito getIbanAppoggio() {
        return ibanAppoggio;
    }

    public void setIbanAppoggio(IbanAccredito ibanAppoggio) {
        this.ibanAppoggio = ibanAppoggio;
    }
}
