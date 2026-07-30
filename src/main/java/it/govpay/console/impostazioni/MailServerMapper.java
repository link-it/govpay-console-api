package it.govpay.console.impostazioni;

import org.springframework.stereotype.Component;

import it.govpay.console.entity.ImpostazioniMailServer;
import it.govpay.console.model.ImpostazioniMailServerCredenziali;
import it.govpay.console.model.ImpostazioniMailServerSsl;
import it.govpay.console.model.MailKeyStore;

/**
 * Conversione bidirezionale tra {@link it.govpay.console.model.ImpostazioniMailServer}
 * e l'entity JPA (riga singola). Le password (SMTP, keystore, truststore)
 * non sono mai lette nel DTO di configurazione: sono gestite dal metodo
 * {@link #applyCredenziali}, chiamato dall'endpoint dedicato.
 */
@Component
public class MailServerMapper {

    public it.govpay.console.model.ImpostazioniMailServer toDto(ImpostazioniMailServer entity) {
        it.govpay.console.model.ImpostazioniMailServer dto = new it.govpay.console.model.ImpostazioniMailServer();
        dto.setAbilitato(entity.isAbilitato());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setUsername(entity.getUsername());
        dto.setFrom(entity.getFromIndirizzo());
        dto.setReadTimeoutMs(entity.getReadTimeoutMs());
        dto.setConnectionTimeoutMs(entity.getConnectionTimeoutMs());
        dto.setStartTls(entity.isStartTls());
        dto.setSsl(toSslDto(entity));
        dto.setPasswordImpostata(entity.isPasswordImpostata());
        return dto;
    }

    private static ImpostazioniMailServerSsl toSslDto(ImpostazioniMailServer entity) {
        ImpostazioniMailServerSsl ssl = new ImpostazioniMailServerSsl();
        ssl.setAbilitato(entity.isSslAbilitato());
        ssl.setTipo(entity.getSslTipo());
        ssl.setHostnameVerifier(entity.isSslHostnameVerifier());
        ssl.setTrustStore(keyStore(entity.getTsLocation(), entity.getTsTipo(), entity.getTsManagementAlgorithm()));
        ssl.setKeyStore(keyStore(entity.getKsLocation(), entity.getKsTipo(), entity.getKsManagementAlgorithm()));
        return ssl;
    }

    private static MailKeyStore keyStore(String location, String tipo, String managementAlgorithm) {
        if (location == null && tipo == null && managementAlgorithm == null) {
            return null;
        }
        MailKeyStore ks = new MailKeyStore();
        ks.setLocation(location);
        ks.setTipo(tipo);
        ks.setManagementAlgorithm(managementAlgorithm);
        return ks;
    }

    /** Aggiorna i campi di configurazione dell'entity esistente (credenziali escluse). */
    public void applyConfig(ImpostazioniMailServer entity, it.govpay.console.model.ImpostazioniMailServer dto) {
        entity.setAbilitato(Boolean.TRUE.equals(dto.getAbilitato()));
        entity.setHost(dto.getHost());
        entity.setPort(dto.getPort());
        entity.setUsername(dto.getUsername());
        entity.setFromIndirizzo(dto.getFrom());
        entity.setReadTimeoutMs(dto.getReadTimeoutMs());
        entity.setConnectionTimeoutMs(dto.getConnectionTimeoutMs());
        entity.setStartTls(Boolean.TRUE.equals(dto.getStartTls()));

        ImpostazioniMailServerSsl ssl = dto.getSsl();
        entity.setSslAbilitato(ssl != null && Boolean.TRUE.equals(ssl.getAbilitato()));
        entity.setSslTipo(ssl != null ? ssl.getTipo() : null);
        entity.setSslHostnameVerifier(ssl != null && Boolean.TRUE.equals(ssl.getHostnameVerifier()));
        MailKeyStore trustStore = ssl != null ? ssl.getTrustStore() : null;
        entity.setTsLocation(trustStore != null ? trustStore.getLocation() : null);
        entity.setTsTipo(trustStore != null ? trustStore.getTipo() : null);
        entity.setTsManagementAlgorithm(trustStore != null ? trustStore.getManagementAlgorithm() : null);
        MailKeyStore keyStore = ssl != null ? ssl.getKeyStore() : null;
        entity.setKsLocation(keyStore != null ? keyStore.getLocation() : null);
        entity.setKsTipo(keyStore != null ? keyStore.getTipo() : null);
        entity.setKsManagementAlgorithm(keyStore != null ? keyStore.getManagementAlgorithm() : null);
    }

    /** Aggiorna solo le credenziali valorizzate nel body (le altre restano invariate). */
    public void applyCredenziali(ImpostazioniMailServer entity, ImpostazioniMailServerCredenziali credenziali) {
        if (credenziali.getNuovaPassword() != null) {
            entity.setPassword(credenziali.getNuovaPassword());
        }
        if (credenziali.getKsPassword() != null) {
            entity.setKsPassword(credenziali.getKsPassword());
        }
        if (credenziali.getTsPassword() != null) {
            entity.setTsPassword(credenziali.getTsPassword());
        }
    }
}
