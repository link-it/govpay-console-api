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
 * Configurazione del connettore Maggioli JPPA di un dominio. A differenza degli
 * altri connettori di notifica pagamenti, Maggioli JPPA non ha una colonna
 * {@code cod_connettore_*} su {@code domini}: il riferimento al connettore, il
 * flag {@code abilitato} e lo stato di avanzamento {@code data_ultima_rt} vivono
 * qui, una riga per dominio.
 * <p>
 * {@code data_ultima_rt} e' stato di runtime scritto dal batch di notifica: non
 * e' esposto dall'API e non va sovrascritto in fase di configurazione.
 */
@Entity
@Table(name = "jppa_config")
@SequenceGenerator(name = "seq_jppa_config", sequenceName = "seq_jppa_config", allocationSize = 1)
public class JppaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_jppa_config")
    @Column(name = "id")
    private Long id;

    @Column(name = "id_dominio", nullable = false)
    private Long idDominio;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "cod_connettore", length = 255)
    private String codConnettore;

    @Column(name = "abilitato", nullable = false)
    private boolean abilitato;

    @Column(name = "data_ultima_rt")
    private OffsetDateTime dataUltimaRt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdDominio() {
        return idDominio;
    }

    public void setIdDominio(Long idDominio) {
        this.idDominio = idDominio;
    }

    public String getCodDominio() {
        return codDominio;
    }

    public void setCodDominio(String codDominio) {
        this.codDominio = codDominio;
    }

    public String getCodConnettore() {
        return codConnettore;
    }

    public void setCodConnettore(String codConnettore) {
        this.codConnettore = codConnettore;
    }

    public boolean isAbilitato() {
        return abilitato;
    }

    public void setAbilitato(boolean abilitato) {
        this.abilitato = abilitato;
    }

    public OffsetDateTime getDataUltimaRt() {
        return dataUltimaRt;
    }

    public void setDataUltimaRt(OffsetDateTime dataUltimaRt) {
        this.dataUltimaRt = dataUltimaRt;
    }
}
