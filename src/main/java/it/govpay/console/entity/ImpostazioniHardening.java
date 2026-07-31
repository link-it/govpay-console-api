package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Configurazione Google reCAPTCHA usata per irrobustire il login. Riga
 * singola, chiave fissa {@link #ID_SINGLETON}. La chiave segreta e' gestita
 * dall'endpoint dedicato {@code .../credenziali}, mai esposta in lettura.
 */
@Entity
@Table(name = "impostazioni_hardening")
public class ImpostazioniHardening {

    public static final Integer ID_SINGLETON = 1;

    @Id
    @Column(name = "id")
    private Integer id = ID_SINGLETON;

    @Column(name = "abilitato", nullable = false)
    private boolean abilitato;

    @Column(name = "server_url", length = 255)
    private String serverUrl;

    @Column(name = "site_key", length = 255)
    private String siteKey;

    @Column(name = "soglia")
    private Double soglia;

    @Column(name = "parametro", length = 255)
    private String parametro;

    @Column(name = "deny_on_fail")
    private boolean denyOnFail;

    @Column(name = "connection_timeout_ms")
    private Integer connectionTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "secret_key", length = 255)
    private String secretKey;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public boolean isAbilitato() {
        return abilitato;
    }

    public void setAbilitato(boolean abilitato) {
        this.abilitato = abilitato;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getSiteKey() {
        return siteKey;
    }

    public void setSiteKey(String siteKey) {
        this.siteKey = siteKey;
    }

    public Double getSoglia() {
        return soglia;
    }

    public void setSoglia(Double soglia) {
        this.soglia = soglia;
    }

    public String getParametro() {
        return parametro;
    }

    public void setParametro(String parametro) {
        this.parametro = parametro;
    }

    public boolean isDenyOnFail() {
        return denyOnFail;
    }

    public void setDenyOnFail(boolean denyOnFail) {
        this.denyOnFail = denyOnFail;
    }

    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
