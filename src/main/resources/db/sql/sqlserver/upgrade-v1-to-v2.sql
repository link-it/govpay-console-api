-- Delta DB per portare una installazione GovPay V1 (SQL Server) allo stato
-- richiesto da govpay-console-api (V2). Sintassi T-SQL.
--
-- Riferimento V1: govpay-381/src/govpay/src/main/resources/db/sql/sqlserver/gov_pay.sql

-- ---------------------------------------------------------------------------
-- Issue #9 - Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
     WHERE name = 'ip_richiedente'
       AND object_id = OBJECT_ID('dbo.gp_audit')
)
    ALTER TABLE gp_audit ADD ip_richiedente VARCHAR(45) NULL;
GO

-- ---------------------------------------------------------------------------
-- Issue #9 scope G - Cursor pagination opt-in su GET /pendenze
-- Indice composito sul sort fisso usato dalla query keyset
-- (dataOraUltimoAggiornamento DESC, id DESC). Senza questo indice la
-- paginazione cursor degrada a scan sequenziale su tabelle grandi.
-- ---------------------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
     WHERE name = 'idx_versamenti_data_ult_agg_id'
       AND object_id = OBJECT_ID('dbo.versamenti')
)
    CREATE INDEX idx_versamenti_data_ult_agg_id
        ON versamenti (data_ora_ultimo_aggiornamento DESC, id DESC);
GO
