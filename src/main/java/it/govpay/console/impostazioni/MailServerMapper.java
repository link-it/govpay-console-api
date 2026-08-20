package it.govpay.console.impostazioni;

import it.govpay.common.configurazione.model.KeyStore;
import it.govpay.common.configurazione.model.MailBatch;
import it.govpay.common.configurazione.model.MailServer;
import it.govpay.common.configurazione.model.SslConfig;
import org.springframework.stereotype.Component;

import it.govpay.console.model.ImpostazioniMailServerCredenziali;
import it.govpay.console.model.ImpostazioniMailServerSsl;
import it.govpay.console.model.MailKeyStore;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniMailServer}
 * e {@link MailBatch} (bean di {@code govpay-common}, deserializzato dal blob
 * {@code configurazione}, chiave {@code mail_batch}). Le password (SMTP,
 * keystore, truststore) non sono mai lette nel DTO di configurazione: sono
 * gestite dal metodo {@link #applyCredenziali}, chiamato dall'endpoint dedicato.
 */
@Component
public class MailServerMapper {

    public it.govpay.console.model.ImpostazioniMailServer toDto(MailBatch source) {
        it.govpay.console.model.ImpostazioniMailServer dto = new it.govpay.console.model.ImpostazioniMailServer();
        MailServer mailserver = source.getMailserver();
        dto.setAbilitato(source.isAbilitato());
        if (mailserver == null) {
            dto.setPasswordImpostata(false);
            return dto;
        }
        dto.setHost(mailserver.getHost());
        dto.setPort(mailserver.getPort());
        dto.setUsername(mailserver.getUsername());
        dto.setFrom(mailserver.getFrom());
        dto.setReadTimeoutMs(mailserver.getReadTimeout());
        dto.setConnectionTimeoutMs(mailserver.getConnectionTimeout());
        dto.setStartTls(mailserver.isStartTls());
        dto.setSsl(toSslDto(mailserver.getSslConfig()));
        dto.setPasswordImpostata(mailserver.getPassword() != null);
        return dto;
    }

    private static ImpostazioniMailServerSsl toSslDto(SslConfig source) {
        ImpostazioniMailServerSsl ssl = new ImpostazioniMailServerSsl();
        if (source == null) {
            return ssl;
        }
        ssl.setAbilitato(source.isAbilitato());
        ssl.setTipo(source.getType());
        ssl.setHostnameVerifier(source.isHostnameVerifier());
        ssl.setTrustStore(keyStoreDto(source.getTrustStore()));
        ssl.setKeyStore(keyStoreDto(source.getKeyStore()));
        return ssl;
    }

    private static MailKeyStore keyStoreDto(KeyStore source) {
        if (source == null || (source.getLocation() == null && source.getType() == null
                && source.getManagementAlgorithm() == null)) {
            return null;
        }
        MailKeyStore ks = new MailKeyStore();
        ks.setLocation(source.getLocation());
        ks.setTipo(source.getType());
        ks.setManagementAlgorithm(source.getManagementAlgorithm());
        return ks;
    }

    /** Applica il DTO su un {@link MailBatch} esistente, preservando le password. */
    public void applyConfig(MailBatch target, it.govpay.console.model.ImpostazioniMailServer dto) {
        target.setAbilitato(Boolean.TRUE.equals(dto.getAbilitato()));

        MailServer esistente = target.getMailserver();
        String passwordEsistente = esistente != null ? esistente.getPassword() : null;
        String ksPasswordEsistente = esistente != null && esistente.getSslConfig() != null
                && esistente.getSslConfig().getKeyStore() != null ? esistente.getSslConfig().getKeyStore().getPassword() : null;
        String tsPasswordEsistente = esistente != null && esistente.getSslConfig() != null
                && esistente.getSslConfig().getTrustStore() != null ? esistente.getSslConfig().getTrustStore().getPassword() : null;

        MailServer mailserver = new MailServer();
        mailserver.setHost(dto.getHost());
        mailserver.setPort(dto.getPort() != null ? dto.getPort() : 0);
        mailserver.setUsername(dto.getUsername());
        mailserver.setFrom(dto.getFrom());
        mailserver.setReadTimeout(dto.getReadTimeoutMs());
        mailserver.setConnectionTimeout(dto.getConnectionTimeoutMs());
        mailserver.setStartTls(Boolean.TRUE.equals(dto.getStartTls()));
        mailserver.setPassword(passwordEsistente);

        ImpostazioniMailServerSsl ssl = dto.getSsl();
        SslConfig sslConfig = new SslConfig();
        sslConfig.setAbilitato(ssl != null && Boolean.TRUE.equals(ssl.getAbilitato()));
        sslConfig.setType(ssl != null ? ssl.getTipo() : null);
        sslConfig.setHostnameVerifier(ssl != null && Boolean.TRUE.equals(ssl.getHostnameVerifier()));
        sslConfig.setTrustStore(keyStoreCommon(ssl != null ? ssl.getTrustStore() : null, tsPasswordEsistente));
        sslConfig.setKeyStore(keyStoreCommon(ssl != null ? ssl.getKeyStore() : null, ksPasswordEsistente));
        mailserver.setSslConfig(sslConfig);

        target.setMailserver(mailserver);
    }

    private static KeyStore keyStoreCommon(MailKeyStore dto, String passwordEsistente) {
        KeyStore ks = new KeyStore();
        ks.setLocation(dto != null ? dto.getLocation() : null);
        ks.setType(dto != null ? dto.getTipo() : null);
        ks.setManagementAlgorithm(dto != null ? dto.getManagementAlgorithm() : null);
        ks.setPassword(passwordEsistente);
        return ks;
    }

    /** Aggiorna solo le credenziali valorizzate nel body (le altre restano invariate). */
    public void applyCredenziali(MailBatch target, ImpostazioniMailServerCredenziali credenziali) {
        if (target.getMailserver() == null) {
            target.setMailserver(new MailServer());
        }
        MailServer mailserver = target.getMailserver();
        if (credenziali.getNuovaPassword() != null) {
            mailserver.setPassword(credenziali.getNuovaPassword());
        }
        if (credenziali.getKsPassword() != null) {
            if (mailserver.getSslConfig() == null) {
                mailserver.setSslConfig(new SslConfig());
            }
            if (mailserver.getSslConfig().getKeyStore() == null) {
                mailserver.getSslConfig().setKeyStore(new KeyStore());
            }
            mailserver.getSslConfig().getKeyStore().setPassword(credenziali.getKsPassword());
        }
        if (credenziali.getTsPassword() != null) {
            if (mailserver.getSslConfig() == null) {
                mailserver.setSslConfig(new SslConfig());
            }
            if (mailserver.getSslConfig().getTrustStore() == null) {
                mailserver.getSslConfig().setTrustStore(new KeyStore());
            }
            mailserver.getSslConfig().getTrustStore().setPassword(credenziali.getTsPassword());
        }
    }
}
