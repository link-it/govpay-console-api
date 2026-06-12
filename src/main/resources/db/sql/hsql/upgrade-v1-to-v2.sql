-- Delta DB per portare una installazione GovPay V1 (HSQLDB) allo stato
-- richiesto da govpay-console-api (V2). Sintassi HSQLDB.
--
-- HSQLDB e' usato tipicamente in installazioni di test/sviluppo: la patch e'
-- mantenuta per parita' con gli altri dialetti supportati da V1.
--
-- Riferimento V1: govpay-381/src/govpay/src/main/resources/db/sql/hsql/gov_pay.sql

-- ---------------------------------------------------------------------------
-- Issue #9 - Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
ALTER TABLE gp_audit ADD COLUMN ip_richiedente VARCHAR(45);

-- ---------------------------------------------------------------------------
-- Issue #9 scope G - Cursor pagination opt-in su GET /pendenze
-- Indice composito sul sort fisso usato dalla query keyset
-- (dataOraUltimoAggiornamento DESC, id DESC).
--
-- Nota: HSQLDB onora la direzione DESC sulle colonne dell'indice.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_versamenti_data_ult_agg_id
    ON versamenti (data_ora_ultimo_aggiornamento DESC, id DESC);
