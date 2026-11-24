-- Eseguito dopo la creazione dello schema da parte di Hibernate (ddl-auto=create-drop).
-- Lo schema di test e' l'unione delle colonne mappate dalle entity della console e da
-- quelle di govpay-common: dove le due mappature divergono sulla nullabilita', le
-- fixture della console (applicazioni-segnaposto senza utenza) richiedono la piu' lasca.
ALTER TABLE applicazioni ALTER COLUMN id_utenza SET NULL;

-- La rpt e' mappata solo da govpay-common-pendenze, che non dichiara il tipo delle colonne
-- binarie: H2 le genera di 255 byte, in produzione sono bytea.
ALTER TABLE rpt ALTER COLUMN xml_rt SET DATA TYPE BYTEA;
ALTER TABLE rpt ALTER COLUMN xml_rpt SET DATA TYPE BYTEA;
