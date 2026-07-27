package it.govpay.console.config;

import java.time.Duration;
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
        private String nome;
        private String descrizione;
        /** Nome del job Spring Batch le cui esecuzioni valorizzano ultimaEsecuzione/prossimaEsecuzione; assente per operazioni non batch-backed. */
        private String jobName;
        /**
         * Durata ISO 8601 (es. PT2H) tra un'esecuzione schedulata e la
         * successiva; assente se non schedulata. Tipo Duration (non String):
         * il binder di Spring Boot valorizza/converte questo campo
         * all'avvio, quindi un valore malformato nelle property fa fallire
         * lo startup dell'applicazione anziche' una GET /operazioni a runtime.
         */
        private Duration frequenzaSchedulata;
        private boolean abilitata = true;
        /**
         * Base URL {@code /api/batch} del microservizio proprietario del
         * job (es. {@code http://iban-batch:8080/api/batch}), usata per
         * l'avvio manuale (POST /operazioni/{id}/esecuzioni). Assente per
         * operazioni non batch-backed o non ancora cablate per l'avvio
         * manuale.
         */
        private String triggerUrl;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getDescrizione() {
            return descrizione;
        }

        public void setDescrizione(String descrizione) {
            this.descrizione = descrizione;
        }

        public String getJobName() {
            return jobName;
        }

        public void setJobName(String jobName) {
            this.jobName = jobName;
        }

        public Duration getFrequenzaSchedulata() {
            return frequenzaSchedulata;
        }

        public void setFrequenzaSchedulata(Duration frequenzaSchedulata) {
            this.frequenzaSchedulata = frequenzaSchedulata;
        }

        public boolean isAbilitata() {
            return abilitata;
        }

        public void setAbilitata(boolean abilitata) {
            this.abilitata = abilitata;
        }

        public String getTriggerUrl() {
            return triggerUrl;
        }

        public void setTriggerUrl(String triggerUrl) {
            this.triggerUrl = triggerUrl;
        }
    }
}
