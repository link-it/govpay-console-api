-- Delta DB per portare una installazione GovPay V1 (HSQLDB) allo stato
-- richiesto da govpay-console-api (V2). Sintassi HSQLDB.
--
-- HSQLDB e' usato tipicamente in installazioni di test/sviluppo: la patch e'
-- mantenuta per parita' con gli altri dialetti supportati da V1.
--

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: Consultazione pendenze
-- Aggiunta colonna ip_richiedente alla tabella gp_audit per gli audit GDPR
-- (PENDENZE_RICERCA_PER_DEBITORE, PENDENZA_VISUALIZZA_DEBITORE).
-- Nullable: il valore proviene da X-Forwarded-For o request.getRemoteAddr() e
-- in scenari edge potrebbe non essere risolvibile.
-- ---------------------------------------------------------------------------
ALTER TABLE gp_audit ADD COLUMN ip_richiedente VARCHAR(45);

-- ---------------------------------------------------------------------------
-- Migrazione V1 -> V2: Cursor pagination opt-in su GET /pendenze
-- Indice composito sul sort fisso usato dalla query keyset
-- (dataOraUltimoAggiornamento DESC, id DESC).
--
-- Nota: HSQLDB onora la direzione DESC sulle colonne dell'indice.
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
ALTER TABLE intermediari DROP COLUMN IF EXISTS cod_connettore_ftp;
