-- Delta DB per portare una installazione GovPay V1 allo stato richiesto da
-- govpay-console-api (V2). Tutte le modifiche di schema introdotte dalla
-- migrazione V2 si accumulano qui in ordine cronologico.
--
-- Riferimento V1: govpay-381/src/govpay/src/main/resources/db/sql/postgresql/gov_pay.sql

-- ---------------------------------------------------------------------------
-- Issue #9 - Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
ALTER TABLE gp_audit ADD COLUMN ip_richiedente VARCHAR(45);
