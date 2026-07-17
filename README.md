<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - Console API

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-console-api/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

API utilizzate dalla Console per il monitoraggio e configurazione di GovPay

## Configurazione porta Actuator / Prometheus

Gli endpoint `/actuator/health` e `/actuator/prometheus` rispondono di default su una porta
**separata** da quella applicativa (`9090`). Per modificarla impostare:

``` bash
management.server.port=[Porta dedicata per gli endpoint actuator]
```

oppure, in ambiente Docker, la variabile d'ambiente equivalente:

``` bash
MANAGEMENT_SERVER_PORT=[Porta dedicata per gli endpoint actuator]
```

## Metriche custom (breakdown interno/esterno)

Oltre alle metriche standard di Spring Boot/Micrometer, la libreria `govpay-common` espone un
breakdown custom (durata delle richieste scomposta in tempo interno/esterno, durata delle chiamate
verso servizi esterni). E' disattivato di default e va abilitato esplicitamente:

``` bash
govpay.metrics.enabled=true
```

Se non abilitato, l'applicazione continua a funzionare normalmente: le classi che dipendono dal
recorder delle metriche ricevono un'implementazione no-op (esegue la chiamata ma non la misura).

