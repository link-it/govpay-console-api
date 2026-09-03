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
 * Tabella di appoggio per il recupero puntuale di una RT su richiesta
 * dell'operatore ({@code POST /ricevute/recuperi}, issue #59 §H).
 * {@code console-api} scrive la tripla {@code (codDominio, iuv, iur)} e
 * innesca il {@code /run} di {@code govpay-rt-batch}, che elabora la riga e la
 * elimina (successo) o la marca con {@code esito} (es. {@code NON_DISPONIBILE}
 * su 404 da BizEvents). Nessuna relazione JPA verso {@link Rpt}/{@link Versamento}:
 * la tabella e' un canale di comunicazione fra i due progetti, non una vista sul
 * dominio pagamenti.
 *
 * <p><b>Nessun vincolo unique sulla tripla</b> — deliberato (issue #59 §10 /
 * #17 §A): richieste ripetute su una tripla ancora pendente restano
 * indipendenti, ognuna la propria riga, nessuna deduplica. Solo una riga già
 * <b>marcata</b> ({@code esito} valorizzato) viene riattivata dal servizio
 * applicativo invece di accumularsi a ogni tentativo.
 */
@Entity
@Table(name = "rt_recuperi")
@SequenceGenerator(name = "seq_rt_recuperi", sequenceName = "seq_rt_recuperi", allocationSize = 1)
public class RtRecupero {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_rt_recuperi")
    @Column(name = "id")
    private Long id;

    @Column(name = "cod_dominio", nullable = false, length = 35)
    private String codDominio;

    @Column(name = "iuv", nullable = false, length = 35)
    private String iuv;

    @Column(name = "iur", nullable = false, length = 35)
    private String iur;

    @Column(name = "data_richiesta", nullable = false)
    private OffsetDateTime dataRichiesta;

    @Column(name = "id_operatore")
    private Long idOperatore;

    @Column(name = "esito", length = 35)
    private String esito;

    @Column(name = "data_ultimo_tentativo")
    private OffsetDateTime dataUltimoTentativo;

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

    public String getIuv() {
        return iuv;
    }

    public void setIuv(String iuv) {
        this.iuv = iuv;
    }

    public String getIur() {
        return iur;
    }

    public void setIur(String iur) {
        this.iur = iur;
    }

    public OffsetDateTime getDataRichiesta() {
        return dataRichiesta;
    }

    public void setDataRichiesta(OffsetDateTime dataRichiesta) {
        this.dataRichiesta = dataRichiesta;
    }

    public Long getIdOperatore() {
        return idOperatore;
    }

    public void setIdOperatore(Long idOperatore) {
        this.idOperatore = idOperatore;
    }

    public String getEsito() {
        return esito;
    }

    public void setEsito(String esito) {
        this.esito = esito;
    }

    public OffsetDateTime getDataUltimoTentativo() {
        return dataUltimoTentativo;
    }

    public void setDataUltimoTentativo(OffsetDateTime dataUltimoTentativo) {
        this.dataUltimoTentativo = dataUltimoTentativo;
    }
}
