-- Eseguito dopo la creazione dello schema da parte di Hibernate (ddl-auto=create-drop).
-- Lo schema di test e' l'unione delle colonne mappate dalle entity della console e da
-- quelle di govpay-common: dove le due mappature divergono sulla nullabilita', le
-- fixture della console (applicazioni-segnaposto senza utenza) richiedono la piu' lasca.
ALTER TABLE applicazioni ALTER COLUMN id_utenza SET NULL;

-- Colonne di versamenti/documenti mappate (NOT NULL) solo dalle entity di
-- govpay-common-pendenze: la console non le conosce e non le valorizza.
ALTER TABLE versamenti ALTER COLUMN aggiornabile SET NULL;
ALTER TABLE versamenti ALTER COLUMN importo_incassato SET NULL;
ALTER TABLE versamenti ALTER COLUMN numero_rata SET NULL;
ALTER TABLE versamenti ALTER COLUMN send_abilitato SET NULL;
ALTER TABLE versamenti ALTER COLUMN stato_pagamento SET NULL;
ALTER TABLE documenti ALTER COLUMN anomalo SET NULL;
ALTER TABLE documenti ALTER COLUMN data_creazione SET NULL;
ALTER TABLE documenti ALTER COLUMN data_ultimo_aggiornamento SET NULL;
ALTER TABLE documenti ALTER COLUMN descrizione SET NULL;
ALTER TABLE documenti ALTER COLUMN id_applicazione SET NULL;
ALTER TABLE documenti ALTER COLUMN id_dominio SET NULL;
ALTER TABLE documenti ALTER COLUMN versione SET NULL;

-- La rpt e' mappata solo da govpay-common-pendenze, che non dichiara il tipo delle colonne
-- binarie: H2 le genera di 255 byte, in produzione sono bytea.
ALTER TABLE rpt ALTER COLUMN xml_rt SET DATA TYPE BYTEA;
ALTER TABLE rpt ALTER COLUMN xml_rpt SET DATA TYPE BYTEA;
