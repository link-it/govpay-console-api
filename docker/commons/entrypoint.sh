#!/bin/bash

##############################################################################
# GovPay Console API - Script di Entrypoint
#
# Supporta nomenclatura variabili stile govpay-docker (GOVPAY_*) e le mappa
# alle property Spring Boot equivalenti.
##############################################################################

set -e

# Debug di esecuzione (come govpay-docker)
exec 6<> /tmp/entrypoint_debug.log
exec 2>&6
set -x

# Funzioni di logging
log_info() { echo -e "\033[0;32m[INFO]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_warn() { echo -e "\033[1;33m[WARN]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }
log_error() { echo -e "\033[0;31m[ERROR]\033[0m $(date '+%Y-%m-%d %H:%M:%S') - $1"; }

log_info "========================================"
log_info "Avvio Servizio GovPay Console API"
log_info "========================================"

##############################################################################
# Supporto variabili stile govpay-docker
##############################################################################



#
# Sanity check variabili minime attese
#
if [ -n "${GOVPAY_DB_TYPE}" -a -n "${GOVPAY_DB_SERVER}" -a -n  "${GOVPAY_DB_USER}" -a -n "${GOVPAY_DB_NAME}" ]
then
        [ -n "${GOVPAY_DB_PASSWORD}" ] || log_warn "La variabile GOVPAY_DB_PASSWORD non è stata impostata."
        log_info "Sanity check variabili obbligatorie ... ok."
else
    log_error "Sanity check variabili obbligatorie ... fallito."
    log_error "Devono essere settate almeno le seguenti variabili obbligatorie:
GOVPAY_DB_TYPE: ${GOVPAY_DB_TYPE}
GOVPAY_DB_SERVER: ${GOVPAY_DB_SERVER}
GOVPAY_DB_NAME: ${GOVPAY_DB_NAME}
GOVPAY_DB_USER: ${GOVPAY_DB_USER}
"
    exit 1
fi


# Imposta valori di default
GOVPAY_DS_JDBC_LIBS=${GOVPAY_DS_JDBC_LIBS:-/opt/jdbc-drivers}
GOVPAY_CONSOLE_API_MIN_POOL=${GOVPAY_CONSOLE_API_MIN_POOL:-2}
GOVPAY_CONSOLE_API_MAX_POOL=${GOVPAY_CONSOLE_API_MAX_POOL:-5}




    # Estrazione host e porta
    IFS=':' read -r DB_HOST DB_PORT <<< "${GOVPAY_DB_SERVER}"
    [ -z "${DB_PORT}" ] && case "${GOVPAY_DB_TYPE}" in
        postgresql) DB_PORT=5432 ;;
        mysql|mariadb) DB_PORT=3306 ;;
        oracle) DB_PORT=1521 ;;
        sqlserver) DB_PORT=1433 ;;
    esac

    # Costruzione URL JDBC
    case "${GOVPAY_DB_TYPE}" in
        postgresql)
            SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            GOVPAY_DS_DRIVER_CLASS="org.postgresql.Driver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.PostgreSQLDialect"
            SPRING_JPA_MAPPING_RESOURCES="META-INF/orm-postgres.xml"
            ;;
        mysql|mariadb)
            SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            GOVPAY_DS_DRIVER_CLASS="com.mysql.cj.jdbc.Driver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.MySQLDialect"
            SPRING_JPA_MAPPING_RESOURCES="META-INF/orm-mysql.xml"
            ;;
        oracle)
            if [ "${GOVPAY_ORACLE_JDBC_URL_TYPE:-servicename}" == "servicename" ]; then
                SPRING_DATASOURCE_URL="jdbc:oracle:thin:@//${DB_HOST}:${DB_PORT}/${GOVPAY_DB_NAME}"
            else
                SPRING_DATASOURCE_URL="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${GOVPAY_DB_NAME}"
            fi
            GOVPAY_DS_DRIVER_CLASS="oracle.jdbc.OracleDriver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.OracleDialect"
            SPRING_JPA_MAPPING_RESOURCES="META-INF/orm-oracle.xml"
            [ -n "${ORACLE_TNS_ADMIN}" ] && JAVA_OPTS="${JAVA_OPTS} -Doracle.net.tns_admin=${ORACLE_TNS_ADMIN}"
            ;;
        sqlserver)
            SPRING_DATASOURCE_URL="jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${GOVPAY_DB_NAME}"
            GOVPAY_DS_DRIVER_CLASS="com.microsoft.sqlserver.jdbc.SQLServerDriver"
            GOVPAY_HYBERNATE_DIALECT="org.hibernate.dialect.SQLServerDialect"
            SPRING_JPA_MAPPING_RESOURCES="META-INF/orm-sqlserver.xml"
            ;;
        *)
            log_error "GOVPAY_DB_TYPE non supportato: ${GOVPAY_DB_TYPE}"
            exit 1
            ;;
    esac

    # Aggiunta parametri di connessione
    [ -n "${GOVPAY_DS_CONN_PARAM}" ] && SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}?${GOVPAY_DS_CONN_PARAM}"

    # Mappatura a variabili Spring

    export SPRING_DATASOURCE_URL
    export SPRING_DATASOURCE_USERNAME="${GOVPAY_DB_USER}"
    export SPRING_DATASOURCE_PASSWORD="${GOVPAY_DB_PASSWORD}"
    export SPRING_DATASOURCE_DRIVER_CLASS_NAME="${GOVPAY_DS_DRIVER_CLASS}"
    export SPRING_JPA_DATABASE_PLATFORM="${GOVPAY_HYBERNATE_DIALECT}"
    export SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT="${GOVPAY_HYBERNATE_DIALECT}"
    export SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${GOVPAY_CONSOLE_API_MIN_POOL}"
    export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${GOVPAY_CONSOLE_API_MAX_POOL}"
    export SPRING_JPA_MAPPING_RESOURCES

    log_info "Database: ${GOVPAY_DB_TYPE} su ${GOVPAY_DB_SERVER}/${GOVPAY_DB_NAME}"

##############################################################################
# Configurazione GovPay API backend
##############################################################################

# API Stampe - mappatura a variabile applicativa app.stampe.base-url
if [ -n "${GOVPAY_API_STAMPE_URL}" ]; then
    export STAMPE_BASE_URL="${GOVPAY_API_STAMPE_URL}"
    log_info "GovPay API Stampe URL: ${GOVPAY_API_STAMPE_URL}"
fi

[ -n "${GOVPAY_API_STAMPE_CONNECT_TIMEOUT_MS}" ] && export STAMPE_CONNECT_TIMEOUT_MS="${GOVPAY_API_STAMPE_CONNECT_TIMEOUT_MS}"
[ -n "${GOVPAY_API_STAMPE_READ_TIMEOUT_MS}" ] && export STAMPE_READ_TIMEOUT_MS="${GOVPAY_API_STAMPE_READ_TIMEOUT_MS}"

##############################################################################
# Identità nodo (GDE / cluster)
##############################################################################

if [ -n "${GOVPAY_CONSOLE_API_CLUSTER_ID}" ]; then
    export CLUSTER_ID="${GOVPAY_CONSOLE_API_CLUSTER_ID}"
    log_info "Cluster ID: ${GOVPAY_CONSOLE_API_CLUSTER_ID}"
fi

##############################################################################
# Configurazione porta server
##############################################################################

if [ -z "${SERVER_PORT}" ]; then
    SERVER_PORT="8080"
    export SERVER_PORT
fi

##############################################################################
# Configurazione Memoria JVM (Percentuale RAM)
##############################################################################

JAVA_OPTS="${JAVA_OPTS:-}"
DEFAULT_MAX_RAM_PERCENTAGE=80

JVM_MEMORY_OPTS="-XX:MaxRAMPercentage=${GOVPAY_CONSOLE_API_JVM_MAX_RAM_PERCENTAGE:-${DEFAULT_MAX_RAM_PERCENTAGE}}"
[ -n "${GOVPAY_CONSOLE_API_JVM_INITIAL_RAM_PERCENTAGE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:InitialRAMPercentage=${GOVPAY_CONSOLE_API_JVM_INITIAL_RAM_PERCENTAGE}"
[ -n "${GOVPAY_CONSOLE_API_JVM_MIN_RAM_PERCENTAGE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MinRAMPercentage=${GOVPAY_CONSOLE_API_JVM_MIN_RAM_PERCENTAGE}"
[ -n "${GOVPAY_CONSOLE_API_JVM_MAX_METASPACE_SIZE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MaxMetaspaceSize=${GOVPAY_CONSOLE_API_JVM_MAX_METASPACE_SIZE}"
[ -n "${GOVPAY_CONSOLE_API_JVM_MAX_DIRECT_MEMORY_SIZE}" ] && JVM_MEMORY_OPTS="$JVM_MEMORY_OPTS -XX:MaxDirectMemorySize=${GOVPAY_CONSOLE_API_JVM_MAX_DIRECT_MEMORY_SIZE}"

JAVA_OPTS="${JAVA_OPTS} ${JVM_MEMORY_OPTS}"
export JAVA_OPTS

##############################################################################
# Riepilogo Configurazione
##############################################################################

log_info "========================================"
log_info "Riepilogo Configurazione"
log_info "========================================"
log_info "Database: ${SPRING_DATASOURCE_URL}"
log_info "Pool: ${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE}/${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE}"
log_info "Mapping ORM: ${SPRING_JPA_MAPPING_RESOURCES}"
log_info "Porta Server: ${SERVER_PORT}"
log_info "Stampe URL: ${STAMPE_BASE_URL:-<non configurato — endpoint PDF rispondono 503>}"
log_info "Java: MaxRAMPercentage=${GOVPAY_CONSOLE_API_JVM_MAX_RAM_PERCENTAGE:-${DEFAULT_MAX_RAM_PERCENTAGE}}%"
log_info "========================================"

##############################################################################
# Avvio Applicazione
##############################################################################

JAR_FILE=$(find /opt/govpay-console-api -name "*.jar" -type f | head -n 1)

if [ -z "${JAR_FILE}" ]; then
    log_error "Nessun file JAR trovato in /opt/govpay-console-api"
    exit 1
fi

log_info "JDBC Drivers: ${GOVPAY_DS_JDBC_LIBS}"
log_info "Avvio: ${JAR_FILE}"
log_info "========================================"

export LOADER_PATH="${GOVPAY_DS_JDBC_LIBS}"
exec java ${JAVA_OPTS} -jar "${JAR_FILE}"
