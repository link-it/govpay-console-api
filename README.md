<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Console API

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-console-api&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-console-api)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-console-api?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-console-api)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-console-api/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

API REST di backend della console di gestione di GovPay ([govpay-console](https://github.com/link-it/govpay-console)): consultazione di pendenze, ricevute, rendicontazioni, riconciliazioni ed eventi, configurazione delle anagrafiche e avvio delle operazioni di manutenzione.

## Sommario

Console API lavora direttamente sul database di GovPay e delega ad altri componenti la stampa dei PDF, il Giornale degli Eventi, l'invio delle ricevute caricate a mano e l'esecuzione dei batch.

Gruppi di operazioni esposti (specifica in [src/main/resources/openapi/openapi.yaml](src/main/resources/openapi/openapi.yaml), consultabile anche con Swagger UI su `/swagger-ui.html`):

| Gruppo | Path principali | Descrizione |
|--------|-----------------|-------------|
| Autenticazione e profilo | `/auth/login`, `/auth/logout`, `/auth/methods`, `/profilo` | Login, logout, metodi di autenticazione attivi, profilo e ACL dell'operatore |
| Pendenze | `/pendenze`, `/pendenze/{idA2A}/{idPendenza}/avviso` | Ricerca e dettaglio delle pendenze, stampa dell'avviso di pagamento in PDF |
| Tracciati | `/pendenze/tracciati` | Caricamento massivo di pendenze (JSON o CSV) e consultazione dell'esito |
| Ricevute | `/ricevute`, `/ricevute/recuperi` | Ricerca delle ricevute, RPT e RT, caricamento manuale di una RT, recupero puntuale da pagoPA |
| Rendicontazioni e riconciliazioni | `/flussi-rendicontazione`, `/riconciliazioni` | Flussi di rendicontazione, riconciliazione degli incassi |
| Giornale degli Eventi | `/eventi` | Ricerca e dettaglio degli eventi, con richiesta e risposta |
| Anagrafiche | `/intermediari`, `/domini`, `/entrate`, `/tipiPendenza`, `/applicazioni`, `/operatori`, `/ruoli` | Intermediari, stazioni, domini (con logo, unità operative, conti di accredito), entrate, tipi pendenza, applicazioni, operatori, ruoli e connettori |
| Dati pagoPA | `/pagopa/enti-creditori`, `/pagopa/iban` | Enti creditori e IBAN registrati su pagoPA, dalla cache aggiornata da `govpay-iban-batch` |
| Impostazioni | `/impostazioni` | Configurazione generale: servizio GDE, giornale eventi, mail, App IO, tracciati CSV, hardening |
| Operazioni | `/operazioni` | Catalogo dei batch, avvio manuale ed esecuzioni |
| Metriche | `/metriche/sla` | Indicatori di rispetto degli SLA pagoPA, letti da Prometheus |

Fuori dalla specifica OpenAPI ci sono `GET /openapi/openapi.yaml` (la specifica stessa) e `/admin/logging/loggers`, per cambiare i livelli di log a caldo.

**Autenticazione e autorizzazione.** L'autenticazione è fornita da [govpay-common-auth](https://github.com/link-it/govpay-common-auth). Sono attivi di default Basic (`Authorization: Basic` a ogni richiesta) e FORM (`POST /auth/login` con credenziali JSON, poi sessione via cookie e token CSRF `XSRF-TOKEN`/`X-XSRF-TOKEN`), entrambi verificati sulle utenze degli operatori nel database di GovPay. Gli altri metodi della libreria (LDAP, SSL, SSL da header, header, API key, SPID, OAuth2) si attivano con le proprietà `govpay.auth.<metodo>.*`. Ogni operazione verifica poi le ACL dell'operatore (servizio e diritto di lettura o scrittura) e la visibilità sui domini e sui tipi pendenza. Le operazioni di scrittura e le consultazioni dei dati sensibili sono registrate nella tabella `gp_audit`.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge e scrive anagrafiche e configurazione (`intermediari`, `stazioni`, `domini`, `uo`, `iban_accredito`, `tributi`, `tipi_tributo`, `tipi_versamento`, `tipi_vers_domini`, `applicazioni`, `operatori`, `utenze`, `utenze_domini`, `utenze_tipo_vers`, `acl`, `connettori`, `jppa_config`, `configurazione`), `tracciati`, `incassi` e `gp_audit`; legge `versamenti`, `singoli_versamenti`, `pagamenti`, `rpt`, `documenti`, `operazioni`, `fr` e `rendicontazioni`. Lo schema è quello del core, alla patch 3.10.0 o successiva: la console non lo crea |
| Tabelle proprie | runtime | sì | `pagopa_ec_cache`, `pagopa_iban_cache` (scritte da `govpay-iban-batch`) e `rt_recuperi`, create dallo script `console-api-schema.sql` del pacchetto `sql.zip`, da applicare dopo lo schema del core |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori da database, client e invio degli eventi al GDE, transaction id e livelli di log da database, metriche |
| [govpay-common-auth](https://github.com/link-it/govpay-common-auth) | build | sì | Catene di autenticazione Spring Security e configurazione dei metodi `govpay.auth.*` |
| [govpay-console](https://github.com/link-it/govpay-console) | runtime | no | Frontend Angular che usa queste API |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Giornale degli Eventi: la console ne legge gli eventi (`GET /eventi`) e gli invia gli eventi delle proprie chiamate (`POST /eventi`, componente `API_BACKOFFICE`). URL e credenziali sono nel connettore `servizioGDE` della tabella `configurazione`, gestito da `/impostazioni/servizioGDE`. Senza connettore abilitato `/eventi` risponde `503` |
| [govpay-stampe-api](https://github.com/link-it/govpay-stampe-api) | runtime | no | Genera i PDF di avvisi e ricevute (`app.stampe.base-url`). Senza URL le stampe rispondono `503` |
| Servizio paForNode di GovPay | runtime | no | Riceve, come `paSendRTV2`, le ricevute caricate a mano con `POST /ricevute` (`app.pafornode.url`): `api-pagopa` del monolite o [govpay-pafornode-api](https://github.com/link-it/govpay-pafornode-api) |
| Batch GovPay | runtime | no | Avviati e consultati da `/operazioni` tramite la loro API `/api/batch`, se censiti in `govpay.operazioni.catalogo`: di default [govpay-iban-batch](https://github.com/link-it/govpay-iban-batch) (`IBAN_CHECK`), `EC_SYNC` e [govpay-rt-batch](https://github.com/link-it/govpay-rt-batch) (`RECUPERO_RT`, usato anche da `POST /ricevute/recuperi`). Con la voce `ELABORAZIONE_RICONCILIAZIONI` nel catalogo, ogni nuova riconciliazione avvia il batch corrispondente |
| [govpay-tracciati-batch](https://github.com/link-it/govpay-tracciati-batch) | runtime | no | Elabora i tracciati di pendenze caricati con `POST /pendenze/tracciati` |
| Prometheus federato | runtime | no | Fonte dei dati di `/metriche/sla` (`govpay.prometheus.url`) |

Le impostazioni scritte dalla console nella tabella `configurazione` (servizio GDE, giornale eventi, mail, App IO, livelli di log) sono lette dagli altri componenti GovPay. La procedura di installazione dell'intero sistema su container è nella [documentazione di GovPay](https://govpay.readthedocs.io/it/3.10.x/installazione/container/index.html).

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install -P jar     # fat jar, per esecuzione standalone e container (profilo di default)
mvn clean install -P war     # war, per il deploy su application server
```

Il profilo sceglie il packaging; il risultato è in `target/govpay-console-api.jar` o `target/govpay-console-api.war`. Il codice delle API, del client di govpay-stampe e dei modelli pagoPA (RPT/RT, paForNode, BizEvents) è generato in fase di build dalle specifiche in `src/main/resources/openapi` e `src/main/resources/xsd`. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze (esecuzione definita in `govpay-bom`); per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -P jar -Dowasp.phase=none
```

Con il profilo `dist` (`mvn clean install -P jar,dist`) si ottiene anche `target/sql.zip`, con lo script `console-api-schema.sql` per PostgreSQL, MySQL, Oracle, SQL Server e HSQL.

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot (es. `app.stampe.base-url` → `APP_STAMPE_BASEURL`). Molte proprietà leggono anche una variabile dedicata, indicata nelle tabelle (es. `STAMPE_BASE_URL`). Con l'immagine Docker si usano le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Server e API

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `server.port` | `SERVER_PORT` | `8080` | Porta HTTP delle API |
| `console.time-zone` | - | `Europe/Rome` | Fuso orario delle date nelle risposte JSON |
| `govpay.console.cluster-id` | `CLUSTER_ID` | `console-api` | Identificativo del nodo negli eventi inviati al GDE |
| `spring.mvc.date-format` | - | `yyyy-MM-dd` | Formato delle date nei parametri |
| `spring.mvc.datetime-format` | - | `yyyy-MM-dd'T'HH:mm:ssZZZ` | Formato di data e ora nei parametri |
| `spring.mvc.throw-exception-if-no-handler-found` | - | `true` | Risponde con un errore applicativo sui path inesistenti |
| `spring.web.resources.add-mappings` | - | `true` | Risorse statiche, necessarie a Swagger UI |
| `spring.jackson.default-property-inclusion` | - | `non_absent` | Esclude dalle risposte JSON i campi nulli o assenti |
| `server.error.whitelabel.enabled` | - | `true` | Pagina di errore di default di Spring Boot |
| `spring.main.banner-mode` | - | `off` | Banner di Spring Boot all'avvio |
| `springdoc.api-docs.enabled` | - | `true` | Infrastruttura di Swagger UI (`/v3/api-docs/swagger-config`) |
| `springdoc.paths-to-exclude` | - | `/**` | Esclude i controller dalla specifica generata: Swagger UI mostra la specifica sorgente |
| `springdoc.swagger-ui.enabled` | - | `true` | Abilita Swagger UI |
| `springdoc.swagger-ui.path` | - | `/swagger-ui.html` | Path di Swagger UI |
| `springdoc.swagger-ui.url` | - | `/openapi/openapi.yaml` | Specifica mostrata da Swagger UI |
| `springdoc.swagger-ui.operations-sorter` | - | `method` | Ordinamento delle operazioni in Swagger UI |
| `springdoc.swagger-ui.tags-sorter` | - | `alpha` | Ordinamento dei gruppi in Swagger UI |

### Autenticazione

Proprietà di [govpay-common-auth](https://github.com/link-it/govpay-common-auth), che ne documenta l'elenco completo (metodi LDAP, SSL, OAuth2 e altri, CORS, header di sicurezza, rate limit del login).

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.auth.basic.enabled` | `true` | Autenticazione HTTP Basic sulle utenze degli operatori |
| `govpay.auth.form.enabled` | `true` | Login JSON `POST /auth/login`, logout `POST /auth/logout`, sessione e CSRF |
| `govpay.auth.public-chain.enabled` | `true` | Path accessibili senza autenticazione, elencati sotto |
| `govpay.auth.public-chain.permit-all-paths[0].path`, `govpay.auth.public-chain.permit-all-paths[0].methods[0]` | `/openapi/openapi.yaml`, `GET` | Specifica OpenAPI |
| `govpay.auth.public-chain.permit-all-paths[1].path` | `/swagger-ui/**` | Risorse di Swagger UI |
| `govpay.auth.public-chain.permit-all-paths[2].path` | `/swagger-ui.html` | Pagina di Swagger UI |
| `govpay.auth.public-chain.permit-all-paths[3].path` | `/v3/api-docs/swagger-config` | Configurazione di Swagger UI |
| `govpay.auth.public-chain.permit-all-paths[4].path`, `govpay.auth.public-chain.permit-all-paths[4].methods[0]` | `/auth/methods`, `GET` | Metodi di autenticazione attivi, per la pagina di login della console |
| `govpay.auth.public-chain.permit-all-paths[5].path`, `govpay.auth.public-chain.permit-all-paths[5].methods[0]` | `/actuator/prometheus`, `GET` | Metriche, servite solo sulla porta di management |
| `govpay.auth.public-chain.permit-all-paths[6].path`, `govpay.auth.public-chain.permit-all-paths[6].methods[0]` | `/actuator/health`, `GET` | Stato, servito solo sulla porta di management |

### Database

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `spring.datasource.url` | `DATASOURCE_URL` | - | URL JDBC del database di GovPay (obbligatoria con il jar) |
| `spring.datasource.username` | `DATASOURCE_USERNAME` | - | Utente del database |
| `spring.datasource.password` | `DATASOURCE_PASSWORD` | - | Password del database |
| `spring.datasource.driver-class-name` | `DATASOURCE_DRIVER` | `org.postgresql.Driver` | Classe del driver JDBC: va cambiata per database diversi da PostgreSQL |
| `spring.datasource.jndi-name` | - | - | Nome JNDI del datasource, per il deploy del war su application server |
| `spring.jpa.database-platform` | `JPA_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` | Dialetto Hibernate: `MySQLDialect`, `OracleDialect`, `SQLServerDialect`, `H2Dialect` per gli altri database |
| `spring.jpa.hibernate.ddl-auto` | `HIBERNATE_DDL_AUTO` | `validate` | Verifica all'avvio che lo schema corrisponda alle entità; lo schema è di GovPay, non va creato dall'applicazione |
| `spring.jpa.open-in-view` | - | `false` | Sessione JPA limitata al servizio, non estesa alla vista |
| `spring.datasource.hikari.minimum-idle` | - | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | - | `10` (`5` nel container) | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | - | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | - | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | - | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Stampe e caricamenti

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `app.stampe.base-url` | `STAMPE_BASE_URL` | - | URL di [govpay-stampe-api](https://github.com/link-it/govpay-stampe-api). Se vuota, la stampa di avvisi e ricevute risponde `503` |
| `app.stampe.connect-timeout-ms` | `STAMPE_CONNECT_TIMEOUT_MS` | `5000` | Timeout di connessione verso govpay-stampe, in ms |
| `app.stampe.read-timeout-ms` | `STAMPE_READ_TIMEOUT_MS` | `30000` | Timeout di lettura verso govpay-stampe, in ms |
| `govpay.stampe.logo.pagopa` | - | logo pagoPA in base64 | Logo pagoPA inserito nelle ricevute PDF (data URI `data:image/png;base64,...`) |
| `app.dominio.logo.max-size-bytes` | `DOMINIO_LOGO_MAX_SIZE_BYTES` | `262144` | Dimensione massima del logo di un dominio (`PUT /domini/{id}/logo`); oltre risponde `413` |
| `app.dominio.logo.cache-max-age-seconds` | `DOMINIO_LOGO_CACHE_MAX_AGE_SECONDS` | `86400` | `max-age` dell'header `Cache-Control` sul logo |
| `govpay.tracciati.max-file-size-mb` | - | `50` | Dimensione massima di un tracciato di pendenze (`POST /pendenze/tracciati`), in MB; oltre risponde `413` |
| `app.ricevute.upload.max-size-bytes` | `RICEVUTE_UPLOAD_MAX_SIZE_BYTES` | `1048576` | Dimensione massima di una RT caricata con `POST /ricevute`; oltre risponde `413` |
| `spring.servlet.multipart.max-file-size` | - | `${govpay.tracciati.max-file-size-mb}MB` | Limite di Spring sul singolo file; allineato al più grande dei limiti applicativi, perché i rifiuti passino dal controllo applicativo e dall'audit |
| `spring.servlet.multipart.max-request-size` | - | `60MB` | Limite di Spring sull'intera richiesta multipart |

### Caricamento e recupero delle ricevute

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `app.pafornode.url` | `PAFORNODE_URL` | - | URL del servizio SOAP paForNode di GovPay, a cui `POST /ricevute` invia la RT |
| `app.pafornode.auth.username` | `PAFORNODE_AUTH_USERNAME` | - | Utente per l'autenticazione Basic verso paForNode (se vuoto, nessuna autenticazione) |
| `app.pafornode.auth.password` | `PAFORNODE_AUTH_PASSWORD` | - | Password per l'autenticazione Basic verso paForNode |
| `app.pafornode.connect-timeout-ms` | `PAFORNODE_CONNECT_TIMEOUT_MS` | `5000` | Timeout di connessione verso paForNode, in ms |
| `app.pafornode.read-timeout-ms` | `PAFORNODE_READ_TIMEOUT_MS` | `30000` | Timeout di lettura verso paForNode, in ms |
| `app.ricevute.recupero.poll-interval-ms` | `RICEVUTE_RECUPERO_POLL_INTERVAL_MS` | `200` | Dopo `POST /ricevute/recuperi`, intervallo di rilettura dello stato del recupero, in ms |
| `app.ricevute.recupero.poll-timeout-ms` | `RICEVUTE_RECUPERO_POLL_TIMEOUT_MS` | `2000` | Attesa massima dell'esito del recupero prima di rispondere `202`, in ms |

### Operazioni e batch

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `govpay.operazioni.catalogo[N].id` | - | vedi sotto | Identificativo dell'operazione |
| `govpay.operazioni.catalogo[N].url` | - | vedi sotto | Base `/api/batch` del batch che esegue l'operazione; assente per le operazioni eseguite dalla console stessa (`RESET_CACHE`) |
| `govpay.operazioni.catalogo[N].abilitata` | - | `true` | Se `false` l'operazione non si può avviare |
| `app.operazioni.trigger.connect-timeout-ms` | `OPERAZIONI_TRIGGER_CONNECT_TIMEOUT_MS` | `5000` | Timeout di connessione verso i batch, in ms |
| `app.operazioni.trigger.read-timeout-ms` | `OPERAZIONI_TRIGGER_READ_TIMEOUT_MS` | `10000` | Timeout di lettura verso i batch, in ms |
| `app.operazioni.trigger.poll-interval-ms` | `OPERAZIONI_TRIGGER_POLL_INTERVAL_MS` | `200` | Dopo l'avvio di un batch, intervallo di attesa della nuova esecuzione, in ms |
| `app.operazioni.trigger.poll-timeout-ms` | `OPERAZIONI_TRIGGER_POLL_TIMEOUT_MS` | `2000` | Attesa massima della nuova esecuzione, in ms |
| `app.operazioni.trigger.async` | - | `true` | Avvio asincrono del batch delle riconciliazioni dopo il salvataggio |
| `govpay.client.cache.enabled` | - | `false` | Tiene in cache i connettori letti da database; l'operazione `RESET_CACHE` la svuota |

Catalogo di default, da adattare ai nomi dei servizi dell'installazione (le voci di batch non installati restano visibili, senza dati):

```properties
govpay.operazioni.catalogo[0].id=IBAN_CHECK
govpay.operazioni.catalogo[0].url=http://iban-batch:8080/api/batch
govpay.operazioni.catalogo[0].abilitata=true
govpay.operazioni.catalogo[1].id=RESET_CACHE
govpay.operazioni.catalogo[1].abilitata=true
govpay.operazioni.catalogo[2].id=EC_SYNC
govpay.operazioni.catalogo[2].url=http://ec-sync-batch:8080/api/batch
govpay.operazioni.catalogo[2].abilitata=true
govpay.operazioni.catalogo[3].id=RECUPERO_RT
govpay.operazioni.catalogo[3].url=http://govpay-rt-batch:8080/api/batch
govpay.operazioni.catalogo[3].abilitata=true
```

Ogni voce si può sovrascrivere anche in forma di variabile d'ambiente, es. `GOVPAY_OPERAZIONI_CATALOGO_0_URL=http://govpay-iban-batch:8080/api/batch`.

### Giornale degli Eventi e audit

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `govpay.giornale-eventi.header-sensibili` | `Authorization,Proxy-Authorization,Cookie,Set-Cookie,X-Api-Key,X-Auth-Token,X-Csrf-Token` | Header oscurati nel dettaglio di richiesta e risposta di un evento, salvo diritto di visualizzarli in chiaro |
| `app.gde.async` | `true` | Invio asincrono al GDE degli eventi delle chiamate |
| `app.gde.pool.core-size` | `2` | Thread minimi per l'invio degli eventi |
| `app.gde.pool.max-size` | `4` | Thread massimi per l'invio degli eventi |
| `app.gde.pool.queue-capacity` | `100` | Eventi in coda in attesa di invio |
| `app.audit.async` | `true` | Scrittura asincrona delle righe di `gp_audit` |
| `app.audit.pool.core-size` | `2` | Thread minimi per l'audit |
| `app.audit.pool.max-size` | `4` | Thread massimi per l'audit |
| `app.audit.pool.queue-capacity` | `100` | Righe di audit in coda |

L'URL del GDE e la politica di registrazione degli eventi non sono proprietà: stanno nella tabella `configurazione` e si gestiscono da `/impostazioni/servizioGDE` e `/impostazioni/giornale-eventi`.

### Metriche SLA

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `govpay.prometheus.url` | `GOVPAY_PROMETHEUS_URL` | `http://localhost:9095` | URL del Prometheus federato da cui si leggono gli indicatori SLA |
| `govpay.metriche.sla.granularitaMinutiMinima` | `GOVPAY_METRICHE_SLA_GRANULARITA_MINUTI_MINIMA` | `5` | Granularità minima, in minuti, accettata da `GET /metriche/sla/{codice}` |
| `app.prometheus.connect-timeout-ms` | - | `5000` | Timeout di connessione verso Prometheus, in ms |
| `app.prometheus.read-timeout-ms` | - | `10000` | Timeout di lettura verso Prometheus, in ms |

### Retry e circuit breaker

Ogni client verso un servizio esterno ha un'istanza Resilience4j di retry e di circuit breaker: `stampe`, `gde`, `prometheus`, `operazioni-trigger`, `pafornode`. Solo i fallimenti di trasporto si ritentano; le risposte che indicano un rifiuto motivato (4xx di govpay-stampe, `409` e `404` dei batch, fault SOAP di paForNode) non contano come guasti e non aprono il circuito. Valori di default:

```properties
resilience4j.retry.instances.stampe.max-attempts=3
resilience4j.retry.instances.stampe.wait-duration=500ms
resilience4j.retry.instances.stampe.retry-exceptions=org.springframework.web.client.ResourceAccessException
resilience4j.circuitbreaker.instances.stampe.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.stampe.minimum-number-of-calls=10
resilience4j.circuitbreaker.instances.stampe.sliding-window-size=20
resilience4j.circuitbreaker.instances.stampe.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.stampe.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.stampe.ignore-exceptions=org.springframework.web.client.HttpClientErrorException

resilience4j.retry.instances.gde.max-attempts=3
resilience4j.retry.instances.gde.wait-duration=500ms
resilience4j.retry.instances.gde.retry-exceptions=org.springframework.web.client.ResourceAccessException
resilience4j.circuitbreaker.instances.gde.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.gde.minimum-number-of-calls=10
resilience4j.circuitbreaker.instances.gde.sliding-window-size=20
resilience4j.circuitbreaker.instances.gde.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.gde.permitted-number-of-calls-in-half-open-state=3

resilience4j.retry.instances.prometheus.max-attempts=3
resilience4j.retry.instances.prometheus.wait-duration=500ms
resilience4j.retry.instances.prometheus.retry-exceptions=org.springframework.web.client.ResourceAccessException
resilience4j.circuitbreaker.instances.prometheus.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.prometheus.minimum-number-of-calls=10
resilience4j.circuitbreaker.instances.prometheus.sliding-window-size=20
resilience4j.circuitbreaker.instances.prometheus.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.prometheus.permitted-number-of-calls-in-half-open-state=3

resilience4j.retry.instances.operazioni-trigger.max-attempts=2
resilience4j.retry.instances.operazioni-trigger.wait-duration=500ms
resilience4j.retry.instances.operazioni-trigger.retry-exceptions=org.springframework.web.client.ResourceAccessException
resilience4j.circuitbreaker.instances.operazioni-trigger.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.operazioni-trigger.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.operazioni-trigger.sliding-window-size=10
resilience4j.circuitbreaker.instances.operazioni-trigger.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.operazioni-trigger.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.operazioni-trigger.ignore-exceptions=org.springframework.web.client.HttpClientErrorException$Conflict,org.springframework.web.client.HttpClientErrorException$NotFound

resilience4j.retry.instances.pafornode.max-attempts=2
resilience4j.retry.instances.pafornode.wait-duration=500ms
resilience4j.retry.instances.pafornode.retry-exceptions=org.springframework.ws.client.WebServiceIOException
resilience4j.circuitbreaker.instances.pafornode.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.pafornode.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.pafornode.sliding-window-size=10
resilience4j.circuitbreaker.instances.pafornode.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.pafornode.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.pafornode.ignore-exceptions=org.springframework.ws.soap.client.SoapFaultClientException
```

### Logging

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `logging.file.name` | `LOGGING_FILE_NAME` | - (solo console) | File di log; nel container `/var/log/govpay-console-api/govpay-console-api.log` |
| `logging.level.it.govpay` | `LOGGING_LEVEL_IT_GOVPAY` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | - | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id (header `X-Correlation-ID` o `X-Request-ID`, restituiti anche in risposta e propagati alle chiamate verso gli altri servizi) |
| `govpay.logging.tracciatura.enabled` | - | `true` | Tracciatura di transaction id e correlation id; le altre proprietà `govpay.logging.tracciatura.*` (header letti e restituiti, validazione degli id) sono descritte in [govpay-common](https://github.com/link-it/govpay-common#tracciatura-e-livelli-di-log) |
| `govpay.logging.livelli-dinamici.enabled` | - | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione`, condivisa da tutti i nodi |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `LOG_LEVEL_REFRESH_INTERVAL` | `60s` | Intervallo di rilettura dei livelli dal database |
| `govpay.logging.livelli-dinamici.ritardo-iniziale` | - | `60s` | Attesa prima della prima rilettura |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | - | `it.govpay` | Prefissi dei logger modificabili da database |

### Monitoraggio

| Proprietà | Variabile | Default | Descrizione |
|-----------|-----------|---------|-------------|
| `management.server.port` | `MANAGEMENT_SERVER_PORT` | `9090` | Porta dedicata agli endpoint actuator: sulla porta delle API non sono serviti |
| `management.endpoints.web.exposure.include` | - | `health,prometheus,loggers` | Endpoint actuator esposti |
| `management.metrics.distribution.percentiles-histogram.http.server.requests` | - | `true` | Istogrammi delle latenze delle API, per aggregare i percentili tra più istanze |
| `management.metrics.distribution.percentiles-histogram.http.client.requests` | - | `true` | Istogrammi delle latenze delle chiamate verso gli altri servizi |
| `management.metrics.distribution.slo.http.server.requests` | - | `100ms,500ms,1s,2s,5s` | Soglie esplicite di latenza, compresa quella SLA pagoPA di 2s |
| `management.metrics.tags.application` | - | `govpay-console-api` | Tag che identifica il servizio nelle metriche |
| `govpay.metrics.enabled` | - | `false` | Metriche aggiuntive di govpay-common: durata delle richieste divisa tra tempo interno e chiamate esterne. Se disattivate le chiamate non vengono misurate, senza altri effetti |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL      | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema del database

La console usa lo schema del core GovPay, aggiornato alla patch 3.10.0 o successiva, più le tabelle proprie dello script `console-api-schema.sql` del dialetto in uso (in `src/main/resources/db/sql/<dialetto>/`, nel pacchetto `sql.zip` della release e in `/opt/sql` dell'immagine di sviluppo). Con `spring.jpa.hibernate.ddl-auto=validate` l'avvio fallisce se manca una tabella o una colonna.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-console-api.jar
```

Le API rispondono sulla porta `8080`, gli endpoint actuator sulla `9090`. Per gli altri database impostare anche `spring.datasource.driver-class-name` e `spring.jpa.database-platform`.

### War su application server

Il war usa il datasource dell'application server, indicato con `spring.datasource.jndi-name` (es. `java:comp/env/it.govpay.datasource`), e non contiene i driver JDBC. Dove trovarlo:

| Origine | Dove si trova |
|---------|---------------|
| Release (tag) | Allegato alla [release GitHub](https://github.com/link-it/govpay-console-api/releases) come `govpay-console-api-<tag>.war` |
| Qualsiasi build | Artifact `govpay-console-api` della run di GitHub Actions |
| Build locale | `mvn clean install -P war`, in `target/govpay-console-api.war` |

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-console-api](https://hub.docker.com/r/linkitaly/govpay-console-api) (release) e `linkitaly/govpay-console-api-dev` (sviluppo, a ogni push su `main`; contiene anche gli script SQL in `/opt/sql`). Le API rispondono sulla porta `8080`, gli endpoint actuator sulla `9090`; i driver JDBC si montano in `/opt/jdbc-drivers`.

```bash
docker run -d --name govpay-console-api -p 8080:8080 -p 9090:9090 \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  -e GOVPAY_API_STAMPE_URL=http://govpay-stampe-api:8080 \
  linkitaly/govpay-console-api
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile e alle variabili dedicate indicate in [Configurazione](#configurazione) (es. `PAFORNODE_URL`, `GOVPAY_PROMETHEUS_URL`, `LOGGING_LEVEL_IT_GOVPAY=DEBUG`):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle`, `sqlserver` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_CONSOLE_API_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_CONSOLE_API_MAX_POOL` | no | `5` | Connessioni massime del pool |
| `GOVPAY_API_STAMPE_URL` | no | - | URL di govpay-stampe-api (diventa `STAMPE_BASE_URL`); senza, le stampe rispondono `503` |
| `GOVPAY_API_STAMPE_CONNECT_TIMEOUT_MS` | no | `5000` | Timeout di connessione verso govpay-stampe (diventa `STAMPE_CONNECT_TIMEOUT_MS`) |
| `GOVPAY_API_STAMPE_READ_TIMEOUT_MS` | no | `30000` | Timeout di lettura verso govpay-stampe (diventa `STAMPE_READ_TIMEOUT_MS`) |
| `GOVPAY_CONSOLE_API_CLUSTER_ID` | no | `console-api` | Identificativo del nodo negli eventi GDE (diventa `CLUSTER_ID`) |
| `SERVER_PORT` | no | `8080` | Porta HTTP delle API |
| `MANAGEMENT_SERVER_PORT` | no | `9090` | Porta degli endpoint actuator |
| `GOVPAY_CONSOLE_API_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_CONSOLE_API_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_CONSOLE_API_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_CONSOLE_API_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_CONSOLE_API_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'entrypoint ricava da `GOVPAY_DB_TYPE` driver, dialetto e URL JDBC, e imposta il pool e i timeout del container indicati in [Database](#database). Il log di avvio dell'entrypoint è in `/tmp/entrypoint_debug.log` dentro il container. Il fuso orario del container è `Europe/Rome` (`TZ`).

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/SCANSIONE.md](docker/SCANSIONE.md); i driver JDBC in [docker/jdbc-drivers/README.md](docker/jdbc-drivers/README.md).

## Monitoraggio

Gli endpoint actuator rispondono solo sulla porta di management (`9090`); `health` e `prometheus` non richiedono autenticazione:

```bash
curl http://localhost:9090/actuator/health       # stato, usato anche dalle probe dei container
curl http://localhost:9090/actuator/prometheus   # metriche in formato Prometheus
```

Sulla stessa porta è esposto anche `loggers`, che cambia i livelli di log del solo nodo interrogato: richiede un operatore autenticato con il diritto sul servizio "Configurazione e manutenzione" (lettura per consultare, scrittura per modificare), altrimenti risponde `401` o `403`. La porta di management va comunque tenuta su una rete interna, perché `health` e `prometheus` sono anonimi. Per cambiare i livelli su tutti i nodi si usa l'API autenticata `/admin/logging/loggers` sulla porta delle API, che richiede il diritto sul servizio "Configurazione e manutenzione" e salva i livelli nella tabella `configurazione`:

```bash
curl -u <operatore>:<password> http://localhost:8080/admin/logging/loggers
curl -u <operatore>:<password> -X PUT -H 'Content-Type: application/json' \
     -d '{"livello":"DEBUG"}' http://localhost:8080/admin/logging/loggers/it.govpay.console
```

## Licenza

GovPay Console API è distribuito con licenza [GNU General Public License v3.0](LICENSE).
