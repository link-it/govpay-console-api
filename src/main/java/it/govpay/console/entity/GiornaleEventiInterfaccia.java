package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Politica di logging verso il Giornale degli Eventi per una singola
 * interfaccia API di GovPay (una riga per interfaccia, chiave naturale
 * {@code nome_interfaccia}). Le 8 interfacce sono un set fisso, mai
 * aggiunto/rimosso a runtime.
 */
@Entity
@Table(name = "giornale_eventi_interfacce")
public class GiornaleEventiInterfaccia {

    @Id
    @Column(name = "nome_interfaccia", length = 20)
    private String nomeInterfaccia;

    @Column(name = "log_letture", nullable = false, length = 20)
    private String logLetture;

    @Column(name = "dump_letture", nullable = false, length = 20)
    private String dumpLetture;

    @Column(name = "log_scritture", nullable = false, length = 20)
    private String logScritture;

    @Column(name = "dump_scritture", nullable = false, length = 20)
    private String dumpScritture;

    public String getNomeInterfaccia() {
        return nomeInterfaccia;
    }

    public void setNomeInterfaccia(String nomeInterfaccia) {
        this.nomeInterfaccia = nomeInterfaccia;
    }

    public String getLogLetture() {
        return logLetture;
    }

    public void setLogLetture(String logLetture) {
        this.logLetture = logLetture;
    }

    public String getDumpLetture() {
        return dumpLetture;
    }

    public void setDumpLetture(String dumpLetture) {
        this.dumpLetture = dumpLetture;
    }

    public String getLogScritture() {
        return logScritture;
    }

    public void setLogScritture(String logScritture) {
        this.logScritture = logScritture;
    }

    public String getDumpScritture() {
        return dumpScritture;
    }

    public void setDumpScritture(String dumpScritture) {
        this.dumpScritture = dumpScritture;
    }
}
