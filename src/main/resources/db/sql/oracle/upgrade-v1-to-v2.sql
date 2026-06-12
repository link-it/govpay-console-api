-- Delta DB per portare una installazione GovPay V1 (Oracle) allo stato
-- richiesto da govpay-console-api (V2). Sintassi Oracle.
--
-- Riferimento V1: govpay-381/src/govpay/src/main/resources/db/sql/oracle/gov_pay.sql

-- ---------------------------------------------------------------------------
-- Issue #9 - Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
ALTER TABLE gp_audit ADD ip_richiedente VARCHAR2(45 CHAR);

-- ---------------------------------------------------------------------------
-- Issue #9 scope G - Cursor pagination opt-in su GET /pendenze
-- Indice composito sul sort fisso usato dalla query keyset
-- (dataOraUltimoAggiornamento DESC, id DESC). Senza questo indice la
-- paginazione cursor degrada a scan sequenziale su tabelle grandi.
--
-- Nota: Oracle supporta indici DESC come function-based index (FBI). Da Oracle
-- 12c+ il DESC sulle colonne dell'indice e' nativo.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_versamenti_data_ult_agg_id
    ON versamenti (data_ora_ultimo_aggiornamento DESC, id DESC);
