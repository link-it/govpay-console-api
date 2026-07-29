package it.govpay.console.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Catalogo configurabile delle operazioni esposte da {@code GET /operazioni}.
 * Le operazioni sempre presenti sono i default spediti in
 * {@code application.properties}; quelle dipendenti da quali batch/connettori
 * sono effettivamente deployati si aggiungono via override delle property
 * nell'ambiente, senza bisogno di un meccanismo dedicato.
 */
@Configuration
@ConfigurationProperties(prefix = "govpay.operazioni")
public class OperazioniProperties {

    private List<OperazioneConfig> catalogo = new ArrayList<>();

    public List<OperazioneConfig> getCatalogo() {
        return catalogo;
    }

    public void setCatalogo(List<OperazioneConfig> catalogo) {
        this.catalogo = catalogo;
    }

    public static class OperazioneConfig {

        private String id;
        /**
         * Base URL {@code /api/batch} del microservizio proprietario del job
         * (es. {@code http://iban-batch:8080/api/batch}, govpay-common
         * {@code AbstractBatchController}): nome/descrizione/schedulazione/
         * esecuzioni si leggono tutte da qui. Assente per operazioni non
         * batch-backed, dispatchate localmente via {@link it.govpay.console.operazioni.OperazioneLocaleHandler}.
         */
        private String url;
        private boolean abilitata = true;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isAbilitata() {
            return abilitata;
        }

        public void setAbilitata(boolean abilitata) {
            this.abilitata = abilitata;
        }
    }
}
