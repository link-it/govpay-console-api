package it.govpay.console.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Server SMTP usato per l'invio dei promemoria. Riga singola, chiave fissa
 * {@link #ID_SINGLETON}. Le password (SMTP, keystore, truststore) sono
 * gestite dall'endpoint dedicato {@code .../password}, mai esposte in lettura.
 */
@Entity
@Table(name = "impostazioni_mail_server")
public class ImpostazioniMailServer {

    public static final Integer ID_SINGLETON = 1;

    @Id
    @Column(name = "id")
    private Integer id = ID_SINGLETON;

    @Column(name = "abilitato", nullable = false)
    private boolean abilitato;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "username", length = 35)
    private String username;

    @Column(name = "from_indirizzo", length = 255)
    private String fromIndirizzo;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "connection_timeout_ms")
    private Integer connectionTimeoutMs;

    @Column(name = "start_tls")
    private boolean startTls;

    @Column(name = "ssl_abilitato", nullable = false)
    private boolean sslAbilitato;

    @Column(name = "ssl_tipo", length = 255)
    private String sslTipo;

    @Column(name = "ssl_hostname_verifier")
    private boolean sslHostnameVerifier;

    @Column(name = "ks_location", length = 255)
    private String ksLocation;

    @Column(name = "ks_tipo", length = 255)
    private String ksTipo;

    @Column(name = "ks_management_algorithm", length = 255)
    private String ksManagementAlgorithm;

    @Column(name = "ts_location", length = 255)
    private String tsLocation;

    @Column(name = "ts_tipo", length = 255)
    private String tsTipo;

    @Column(name = "ts_management_algorithm", length = 255)
    private String tsManagementAlgorithm;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "ks_password", length = 255)
    private String ksPassword;

    @Column(name = "ts_password", length = 255)
    private String tsPassword;

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

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFromIndirizzo() {
        return fromIndirizzo;
    }

    public void setFromIndirizzo(String fromIndirizzo) {
        this.fromIndirizzo = fromIndirizzo;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public boolean isStartTls() {
        return startTls;
    }

    public void setStartTls(boolean startTls) {
        this.startTls = startTls;
    }

    public boolean isSslAbilitato() {
        return sslAbilitato;
    }

    public void setSslAbilitato(boolean sslAbilitato) {
        this.sslAbilitato = sslAbilitato;
    }

    public String getSslTipo() {
        return sslTipo;
    }

    public void setSslTipo(String sslTipo) {
        this.sslTipo = sslTipo;
    }

    public boolean isSslHostnameVerifier() {
        return sslHostnameVerifier;
    }

    public void setSslHostnameVerifier(boolean sslHostnameVerifier) {
        this.sslHostnameVerifier = sslHostnameVerifier;
    }

    public String getKsLocation() {
        return ksLocation;
    }

    public void setKsLocation(String ksLocation) {
        this.ksLocation = ksLocation;
    }

    public String getKsTipo() {
        return ksTipo;
    }

    public void setKsTipo(String ksTipo) {
        this.ksTipo = ksTipo;
    }

    public String getKsManagementAlgorithm() {
        return ksManagementAlgorithm;
    }

    public void setKsManagementAlgorithm(String ksManagementAlgorithm) {
        this.ksManagementAlgorithm = ksManagementAlgorithm;
    }

    public String getTsLocation() {
        return tsLocation;
    }

    public void setTsLocation(String tsLocation) {
        this.tsLocation = tsLocation;
    }

    public String getTsTipo() {
        return tsTipo;
    }

    public void setTsTipo(String tsTipo) {
        this.tsTipo = tsTipo;
    }

    public String getTsManagementAlgorithm() {
        return tsManagementAlgorithm;
    }

    public void setTsManagementAlgorithm(String tsManagementAlgorithm) {
        this.tsManagementAlgorithm = tsManagementAlgorithm;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKsPassword() {
        return ksPassword;
    }

    public void setKsPassword(String ksPassword) {
        this.ksPassword = ksPassword;
    }

    public String getTsPassword() {
        return tsPassword;
    }

    public void setTsPassword(String tsPassword) {
        this.tsPassword = tsPassword;
    }

    public boolean isPasswordImpostata() {
        return password != null && !password.isBlank();
    }
}
