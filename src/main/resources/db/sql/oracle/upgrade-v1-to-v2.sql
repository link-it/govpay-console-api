-- Delta DB per portare una installazione GovPay V1 (Oracle) allo stato
-- richiesto da govpay-console-api (V2). Sintassi Oracle.
--

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
ALTER TABLE gp_audit ADD ip_richiedente VARCHAR2(45 CHAR);

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: Cursor pagination opt-in su GET /pendenze
-- Indice composito sul sort fisso usato dalla query keyset
-- (dataOraUltimoAggiornamento DESC, id DESC). Senza questo indice la
-- paginazione cursor degrada a scan sequenziale su tabelle grandi.
--
-- Nota: Oracle supporta indici DESC come function-based index (FBI). Da Oracle
-- 12c+ il DESC sulle colonne dell'indice e' nativo.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_versamenti_data_ult_agg_id
    ON versamenti (data_ora_ultimo_aggiornamento DESC, id DESC);

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: CRUD Connettori
-- Il connettore FTP dell'intermediario non e' gestito da V2 ed e' eliminato del
-- tutto: si rimuovono le sue proprieta' dalla tabella connettori (riferite da
-- cod_connettore_ftp) e si elimina la colonna di riferimento da intermediari.
-- ---------------------------------------------------------------------------
DELETE FROM connettori WHERE cod_connettore IN (
    SELECT cod_connettore_ftp FROM intermediari WHERE cod_connettore_ftp IS NOT NULL);
ALTER TABLE intermediari DROP COLUMN cod_connettore_ftp;

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: Consultazione ricevute (GET /ricevute)
-- Indice composito sul sort fisso della query keyset cursor
-- (data_msg_ricevuta DESC, id DESC). Stessa motivazione dell'indice su
-- versamenti: senza, la paginazione cursor degrada a scan sequenziale.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_rpt_data_msg_ricevuta_id
    ON rpt (data_msg_ricevuta DESC, id DESC);
