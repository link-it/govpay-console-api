-- Schema target di govpay-console-api per PostgreSQL.
--
-- Tabelle proprie di console-api. Le modifiche alle tabelle ereditate da
-- govpay-core sono nello script `upgrade-v1-to-v2.sql`.
--
-- Per applicare il bring-up completo di un DB da zero per console-api:
--   1) applicare lo schema di govpay-core (`gov_pay.sql` di govpay-381)
--   2) applicare `upgrade-v1-to-v2.sql` di questo progetto
--   3) applicare questo file

-- Cache locale dell'anagrafica Enti Creditori sincronizzata da pagoPA
-- (scritta dal batch govpay-iban-batch, letta in sola lettura da console-api).
CREATE SEQUENCE seq_pagopa_ec_cache start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE pagopa_ec_cache
(
	cod_fiscale VARCHAR(16) NOT NULL,
	denominazione VARCHAR(255) NOT NULL,
	station_id VARCHAR(35),
	aux_digit VARCHAR(2) NOT NULL,
	segregation_code VARCHAR(4),
	cbill_code VARCHAR(35),
	data_ultimo_aggiornamento TIMESTAMP NOT NULL,
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_pagopa_ec_cache') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_ec_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_ec_cache_cf UNIQUE (cod_fiscale)
);

-- Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
-- assistito dei conti di accredito (scritta dal batch govpay-iban-batch, letta
-- in sola lettura da console-api).
CREATE SEQUENCE seq_pagopa_iban_cache start 1 increment 1 maxvalue 9223372036854775807 minvalue 1 cache 1 NO CYCLE;

CREATE TABLE pagopa_iban_cache
(
	cod_dominio VARCHAR(35) NOT NULL,
	iban VARCHAR(35) NOT NULL,
	attivo BOOLEAN NOT NULL,
	data_ultima_verifica TIMESTAMP NOT NULL,
	-- fk/pk columns
	id BIGINT DEFAULT nextval('seq_pagopa_iban_cache') NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_pagopa_iban_cache PRIMARY KEY (id),
	CONSTRAINT uq_pagopa_iban_cache_dominio_iban UNIQUE (cod_dominio, iban)
);

-- Politica di logging verso il Giornale degli Eventi, una riga per ciascuna
-- delle 8 interfacce API di GovPay. Sotto-risorsa `giornaleEventi` del blob
-- V1 `configurazione` (il connettore GDE vero e proprio e' sulla tabella
-- condivisa `connettori`, cod_connettore 'govpay_gde_api').
CREATE TABLE giornale_eventi_interfacce
(
	nome_interfaccia VARCHAR(20) NOT NULL,
	log_letture VARCHAR(20) NOT NULL,
	dump_letture VARCHAR(20) NOT NULL,
	log_scritture VARCHAR(20) NOT NULL,
	dump_scritture VARCHAR(20) NOT NULL,
	-- fk/pk keys constraints
	CONSTRAINT pk_giornale_eventi_interfacce PRIMARY KEY (nome_interfaccia)
);

-- Server SMTP usato per l'invio dei promemoria (riga singola). Le password
-- (SMTP, keystore, truststore) sono scritte solo dall'endpoint dedicato
-- `.../password`, mai esposte in lettura.
CREATE TABLE impostazioni_mail_server
(
	id SMALLINT NOT NULL,
	abilitato BOOLEAN NOT NULL,
	host VARCHAR(255),
	port INTEGER,
	username VARCHAR(35),
	from_indirizzo VARCHAR(255),
	read_timeout_ms INTEGER,
	connection_timeout_ms INTEGER,
	start_tls BOOLEAN NOT NULL,
	ssl_abilitato BOOLEAN NOT NULL,
	ssl_tipo VARCHAR(255),
	ssl_hostname_verifier BOOLEAN NOT NULL,
	ks_location VARCHAR(255),
	ks_tipo VARCHAR(255),
	ks_management_algorithm VARCHAR(255),
	ts_location VARCHAR(255),
	ts_tipo VARCHAR(255),
	ts_management_algorithm VARCHAR(255),
	password VARCHAR(255),
	ks_password VARCHAR(255),
	ts_password VARCHAR(255),
	CONSTRAINT pk_impostazioni_mail_server PRIMARY KEY (id)
);

-- Template FreeMarker dei promemoria spediti via mail, una riga per tipo
-- (AVVISO/RICEVUTA/SCADENZA).
CREATE TABLE impostazioni_mail_promemoria
(
	tipo_promemoria VARCHAR(20) NOT NULL,
	oggetto VARCHAR(4000),
	messaggio VARCHAR(4000),
	allega_pdf BOOLEAN,
	solo_eseguiti BOOLEAN,
	preavviso INTEGER,
	CONSTRAINT pk_impostazioni_mail_promemoria PRIMARY KEY (tipo_promemoria)
);

-- Template FreeMarker dei promemoria spediti via notifica push App IO, una
-- riga per tipo (AVVISO/RICEVUTA/SCADENZA). Nessun allegato (a differenza
-- della variante mail): niente colonna allega_pdf.
CREATE TABLE impostazioni_appio_promemoria
(
	tipo_promemoria VARCHAR(20) NOT NULL,
	oggetto VARCHAR(4000),
	messaggio VARCHAR(4000),
	solo_eseguiti BOOLEAN,
	preavviso INTEGER,
	CONSTRAINT pk_impostazioni_appio_promemoria PRIMARY KEY (tipo_promemoria)
);

-- Template FreeMarker per la generazione dei tracciati CSV di risposta (riga singola).
CREATE TABLE impostazioni_tracciati_csv
(
	id SMALLINT NOT NULL,
	tipo VARCHAR(20),
	intestazione VARCHAR(4000),
	richiesta VARCHAR(4000),
	risposta VARCHAR(4000),
	CONSTRAINT pk_impostazioni_tracciati_csv PRIMARY KEY (id)
);
